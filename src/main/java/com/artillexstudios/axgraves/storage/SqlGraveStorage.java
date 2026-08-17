package com.artillexstudios.axgraves.storage;

import com.artillexstudios.axapi.database.DatabaseConfig;
import com.artillexstudios.axapi.database.DatabaseHandler;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The default/recommended storage backend. Persists live graves incrementally (one row per
 * grave, updated in place) rather than rewriting a whole-file snapshot on every change, and
 * supports grave history/restore (feature C) via a second table.
 *
 * <p>Uses AxAPI's {@link DatabaseHandler} (HikariCP-backed) rather than a hand-rolled
 * connection pool. Schema/queries are issued as raw SQL via {@link DatabaseHandler#connection()}
 * rather than AxAPI's {@code query(name)}/classpath-SQL-file mechanism, to keep this class
 * self-contained and its table-prefix handling explicit and easy to verify.</p>
 */
public class SqlGraveStorage implements GraveStorage {
    private final DatabaseConfig config;
    private final Supplier<Connection> connectionSupplier;
    private final String tablePrefix;
    private final boolean historyEnabled;
    private final int keepPerPlayer;
    private final int keepDays;

    private DatabaseHandler handler;

    public SqlGraveStorage(@NotNull DatabaseConfig config, boolean historyEnabled, int keepPerPlayer, int keepDays) {
        this(config, null, historyEnabled, keepPerPlayer, keepDays);
    }

    /**
     * Test/advanced seam: bypasses AxAPI's Hikari-config-building ({@code DatabaseType#config})
     * by handing {@link DatabaseHandler} a ready-made connection supplier directly (its
     * documented two-arg constructor). Used by {@code SqlGraveStorageTest} to point at
     * {@code jdbc:h2:mem:} without depending on unverified internals of how AxAPI turns a
     * {@link DatabaseConfig} into a JDBC URL for each database type.
     */
    SqlGraveStorage(@NotNull DatabaseConfig config, Supplier<Connection> connectionSupplier,
                     boolean historyEnabled, int keepPerPlayer, int keepDays) {
        this.config = config;
        this.connectionSupplier = connectionSupplier;
        this.tablePrefix = config.tablePrefix() == null ? "" : config.tablePrefix();
        this.historyEnabled = historyEnabled;
        this.keepPerPlayer = keepPerPlayer;
        this.keepDays = keepDays;
    }

    private String graves() {
        return tablePrefix + "graves";
    }

    private String history() {
        return tablePrefix + "grave_history";
    }

    @Override
    public void init() {
        handler = connectionSupplier != null ? new DatabaseHandler(config, connectionSupplier) : new DatabaseHandler(config);

        try (Connection c = handler.connection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + graves() + " (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "owner VARCHAR(36) NOT NULL," +
                    "owner_name VARCHAR(32)," +
                    "location VARCHAR(255) NOT NULL," +
                    "items BLOB," +
                    "data_version INT," +
                    "stored_xp INT NOT NULL DEFAULT 0," +
                    "created_at BIGINT NOT NULL)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + history() + " (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "owner VARCHAR(36) NOT NULL," +
                    "owner_name VARCHAR(32)," +
                    "location VARCHAR(255) NOT NULL," +
                    "items BLOB," +
                    "data_version INT," +
                    "stored_xp INT NOT NULL DEFAULT 0," +
                    "created_at BIGINT NOT NULL," +
                    "end_reason VARCHAR(16) NOT NULL," +
                    "ended_at BIGINT NOT NULL," +
                    "restored BOOLEAN NOT NULL DEFAULT FALSE)");

            st.executeUpdate("CREATE INDEX IF NOT EXISTS " + tablePrefix + "grave_history_owner_idx ON " + history() + " (owner, ended_at DESC)");
        } catch (SQLException ex) {
            handler.close();
            handler = null;
            throw new IllegalStateException("failed to initialize grave storage schema", ex);
        }
    }

    @Override
    @NotNull
    public List<GraveRecord> loadAll() {
        List<GraveRecord> result = new ArrayList<>();
        String sql = "SELECT id, owner, owner_name, location, items, data_version, stored_xp, created_at FROM " + graves();

        try (Connection c = handler.connection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(readLiveRecord(rs));
            }
        } catch (SQLException ex) {
            LogUtils.error("failed to load graves from the database", ex);
        }

        return result;
    }

    @Override
    public long save(@NotNull GraveRecord record) {
        if (record.id() > 0) {
            String updateSql = "UPDATE " + graves() + " SET owner=?, owner_name=?, location=?, items=?, data_version=?, stored_xp=?, created_at=? WHERE id=?";
            try (Connection c = handler.connection(); PreparedStatement ps = c.prepareStatement(updateSql)) {
                bindGrave(ps, record);
                ps.setLong(8, record.id());
                if (ps.executeUpdate() > 0) return record.id();
                // fell through: the row no longer exists (e.g. wiped externally) - insert fresh below
            } catch (SQLException ex) {
                LogUtils.error("failed to update a grave in the database", ex);
                return record.id();
            }
        }

        String insertSql = "INSERT INTO " + graves() + " (owner, owner_name, location, items, data_version, stored_xp, created_at) VALUES (?,?,?,?,?,?,?)";
        try (Connection c = handler.connection(); PreparedStatement ps = c.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            bindGrave(ps, record);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException ex) {
            LogUtils.error("failed to insert a grave into the database", ex);
        }

        return record.id();
    }

    private void bindGrave(@NotNull PreparedStatement ps, @NotNull GraveRecord record) throws SQLException {
        ps.setString(1, record.owner().toString());
        ps.setString(2, record.ownerName());
        ps.setString(3, record.location());
        ps.setBytes(4, record.items());
        ps.setInt(5, record.dataVersion());
        ps.setInt(6, record.storedXP());
        ps.setLong(7, record.createdAt());
    }

    @Override
    public void remove(long id, @NotNull EndReason reason) {
        try (Connection c = handler.connection()) {
            c.setAutoCommit(false);
            try {
                UUID owner = archiveAndDelete(c, id, reason);
                if (historyEnabled && owner != null) pruneHistory(c, owner);
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            LogUtils.error("failed to remove/archive grave {} in the database", id, ex);
        }
    }

    @NotNull
    private UUID archiveAndDelete(@NotNull Connection c, long id, @NotNull EndReason reason) throws SQLException {
        UUID owner = null;

        if (historyEnabled) {
            String selectSql = "SELECT owner, owner_name, location, items, data_version, stored_xp, created_at FROM " + graves() + " WHERE id=?";
            try (PreparedStatement sel = c.prepareStatement(selectSql)) {
                sel.setLong(1, id);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        owner = UUID.fromString(rs.getString("owner"));

                        String insertHist = "INSERT INTO " + history() + " (owner, owner_name, location, items, data_version, stored_xp, created_at, end_reason, ended_at, restored) VALUES (?,?,?,?,?,?,?,?,?,FALSE)";
                        try (PreparedStatement ins = c.prepareStatement(insertHist)) {
                            ins.setString(1, rs.getString("owner"));
                            ins.setString(2, rs.getString("owner_name"));
                            ins.setString(3, rs.getString("location"));
                            ins.setBytes(4, rs.getBytes("items"));
                            ins.setInt(5, rs.getInt("data_version"));
                            ins.setInt(6, rs.getInt("stored_xp"));
                            ins.setLong(7, rs.getLong("created_at"));
                            ins.setString(8, reason.name());
                            ins.setLong(9, System.currentTimeMillis());
                            ins.executeUpdate();
                        }
                    }
                }
            }
        }

        try (PreparedStatement del = c.prepareStatement("DELETE FROM " + graves() + " WHERE id=?")) {
            del.setLong(1, id);
            del.executeUpdate();
        }

        return owner;
    }

    private void pruneHistory(@NotNull Connection c, @NotNull UUID owner) throws SQLException {
        if (keepDays > 0) {
            long cutoff = System.currentTimeMillis() - keepDays * 86_400_000L;
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + history() + " WHERE ended_at < ?")) {
                ps.setLong(1, cutoff);
                ps.executeUpdate();
            }
        }

        if (keepPerPlayer > 0) {
            // nested-derived-table form rather than a window function, for portability across
            // H2/MySQL/SQLite
            String sql = "DELETE FROM " + history() + " WHERE owner = ? AND id NOT IN (" +
                    "SELECT id FROM (SELECT id FROM " + history() + " WHERE owner = ? ORDER BY ended_at DESC LIMIT ?) AS keep_ids)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, owner.toString());
                ps.setString(2, owner.toString());
                ps.setInt(3, keepPerPlayer);
                ps.executeUpdate();
            }
        }
    }

    @Override
    @NotNull
    public List<GraveRecord> history(@NotNull UUID owner, int limit) {
        List<GraveRecord> result = new ArrayList<>();
        if (!historyEnabled) return result;

        String sql = "SELECT id, owner, owner_name, location, items, data_version, stored_xp, created_at, end_reason, ended_at, restored FROM "
                + history() + " WHERE owner=? ORDER BY ended_at DESC LIMIT ?";
        try (Connection c = handler.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readHistoryRecord(rs));
                }
            }
        } catch (SQLException ex) {
            LogUtils.error("failed to load grave history for {}", owner, ex);
        }

        return result;
    }

    @Override
    @NotNull
    public Optional<GraveRecord> historyEntry(long historyId) {
        if (!historyEnabled) return Optional.empty();

        String sql = "SELECT id, owner, owner_name, location, items, data_version, stored_xp, created_at, end_reason, ended_at, restored FROM "
                + history() + " WHERE id=?";
        try (Connection c = handler.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, historyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(readHistoryRecord(rs));
            }
        } catch (SQLException ex) {
            LogUtils.error("failed to load grave history entry {}", historyId, ex);
        }

        return Optional.empty();
    }

    @Override
    public boolean claimForRestore(long historyId) {
        if (!historyEnabled) return false;

        // the WHERE restored = FALSE clause is what makes this atomic: exactly one caller ever
        // sees executeUpdate() return > 0 for a given id, even under concurrent admin commands.
        String sql = "UPDATE " + history() + " SET restored = TRUE WHERE id = ? AND restored = FALSE";
        try (Connection c = handler.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, historyId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LogUtils.error("failed to claim grave history entry {} for restore", historyId, ex);
            return false;
        }
    }

    @Override
    public void close() {
        if (handler != null) handler.close();
    }

    @NotNull
    private GraveRecord readLiveRecord(@NotNull ResultSet rs) throws SQLException {
        return new GraveRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner")),
                rs.getString("owner_name"),
                rs.getString("location"),
                rs.getBytes("items"),
                rs.getInt("data_version"),
                rs.getInt("stored_xp"),
                rs.getLong("created_at"),
                null, null
        );
    }

    @NotNull
    private GraveRecord readHistoryRecord(@NotNull ResultSet rs) throws SQLException {
        EndReason reason;
        try {
            reason = EndReason.valueOf(rs.getString("end_reason"));
        } catch (IllegalArgumentException ex) {
            reason = EndReason.REMOVED;
        }

        return new GraveRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner")),
                rs.getString("owner_name"),
                rs.getString("location"),
                rs.getBytes("items"),
                rs.getInt("data_version"),
                rs.getInt("stored_xp"),
                rs.getLong("created_at"),
                reason,
                rs.getLong("ended_at"),
                rs.getBoolean("restored")
        );
    }
}
