package group.aelysium.rustyconnector.modules.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import group.aelysium.rustyconnector.common.haze.HazeDatabase;
import group.aelysium.rustyconnector.common.modules.Module;
import group.aelysium.rustyconnector.modules.postgres.lib.LocalDateTimeSerializer;
import group.aelysium.rustyconnector.modules.postgres.requests.*;
import group.aelysium.rustyconnector.shaded.com.google.code.gson.gson.Gson;
import group.aelysium.rustyconnector.shaded.com.google.code.gson.gson.GsonBuilder;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.exceptions.HazeCastingException;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.exceptions.HazeException;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.lib.DataHolder;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.requests.*;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

public class PostgresDatabase extends HazeDatabase implements Module {
    public static final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
        .create();
    
    protected final AtomicBoolean closed = new AtomicBoolean(false);
    protected final HikariDataSource dataSource;
    protected final String address;
    protected final int port;
    protected final String username;
    protected final String password;
    protected final int poolSize;

    public PostgresDatabase(
        @NotNull String database,
        @NotNull String address,
        int port,
        @NotNull String username,
        @NotNull String password,
        int poolSize
    ) throws SQLException {
        super(database, Type.TABULAR);
        this.address = address;
        this.port = port;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        
        try {
            Class.forName("org.postgresql.Driver");
        } catch(Exception ignore) {}
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://"+this.address+":"+this.port+"/"+this.name);
        config.setUsername(this.username);
        config.setPassword(this.password);
        config.setMaximumPoolSize(this.poolSize);
        config.setLeakDetectionThreshold(2000);
        config.setConnectionTimeout(60000);
        config.addDataSourceProperty("ssl", "false");
        
        this.dataSource = new HikariDataSource(config);
    }

    public HikariDataSource dataSource() {
        return this.dataSource;
    }

    @Override
    public CreateRequest newCreateRequest(@NotNull String target) {
        if(this.closed.get()) throw new HazeException("This HazeDatabase connection is closed.");
        
        return new PostgresCreateRequest(this, target);
    }

    @Override
    public ReadRequest newReadRequest(@NotNull String target) {
        if(this.closed.get()) throw new HazeException("This HazeDatabase connection is closed.");
        
        return new PostgresReadRequest(this, target);
    }

    @Override
    public UpdateRequest newUpdateRequest(@NotNull String target) {
        if(this.closed.get()) throw new HazeException("This HazeDatabase connection is closed.");
        
        return new PostgresUpdateRequest(this, target);
    }

    @Override
    public UpsertRequest newUpsertRequest(@NotNull String target) {
        if(this.closed.get()) throw new HazeException("This HazeDatabase connection is closed.");

        return new PostgresUpsertRequest(this, target);
    }
    
    @Override
    public DeleteRequest newDeleteRequest(@NotNull String target) {
        if(this.closed.get()) throw new HazeException("This HazeDatabase connection is closed.");
        
        return new PostgresDeleteRequest(this, target);
    }

    @Override
    public void createDataHolder(@NotNull DataHolder dataHolder) throws HazeException {
        if(this.closed.get()) throw new HazeException("This HazeDatabase connection is closed.");
        
        String tableName = dataHolder.name();
        StringJoiner columnDefinitions = new StringJoiner(", ");

        if(dataHolder.keys().values().stream().noneMatch(group.aelysium.rustyconnector.shaded.group.aelysium.haze.lib.Type::primaryKey))
            columnDefinitions.add("id INT SERIAL PRIMARY KEY");

        dataHolder.keys().forEach((k, v) -> {
            StringBuilder columnDefinition = new StringBuilder();
            columnDefinition.append(k)
                .append(" ")
                .append(getPostgresType(v));

            if (!v.nullable()) columnDefinition.append(" NOT NULL");
            if (v.unique()) columnDefinition.append(" UNIQUE");
            if (v.primaryKey()) columnDefinition.append(" PRIMARY KEY");

            columnDefinitions.add(columnDefinition.toString());
        });

        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + columnDefinitions + ")";

        try (
            Connection connection = this.dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(createTableSQL)
        ) {
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new HazeException("Error creating table: `" + tableName + "`. "+e.getMessage());
        }
    }

    private String getPostgresType(group.aelysium.rustyconnector.shaded.group.aelysium.haze.lib.Type key) {
        if(this.closed.get()) throw new HazeException("This HazeDatabase connection is closed.");
        
        return switch (key.type()) {
            case STRING -> {
                if (key.length() == -1) yield "TEXT";
                if (key.length() > 0) yield "VARCHAR(" + key.length() + ")";
                yield "TEXT";
            }
            case INTEGER -> {
                if (key.length() == 1) yield "SMALLINT";
                if (key.length() <= 3) yield "SMALLINT";
                if (key.length() <= 5) yield "INTEGER";
                if (key.length() <= 8) yield "BIGINT";
                yield "INTEGER";
            }
            case UNSIGNED_INTEGER -> {
                if (key.length() == 1) yield "SMALLINT";
                if (key.length() <= 3) yield "INTEGER";
                if (key.length() <= 5) yield "BIGINT";
                if (key.length() <= 8) yield "BIGINT"; // No UNSIGNED BIGINT in Postgres, fallback to BIGINT or NUMERIC
                yield "BIGINT";
            }
            case DECIMAL -> {
                if (key.length() > 0) yield "DECIMAL(" + key.length() + ", 2)";
                yield "DECIMAL(10, 2)";
            }
            case UNSIGNED_DECIMAL -> {
                if (key.length() > 0) yield "DECIMAL(" + key.length() + ", 2)";
                yield "DECIMAL(10, 2)";
            }
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case DATETIME -> "TIMESTAMP";
            case TIME -> "TIME";
            case BINARY -> "BYTEA";
            case ARRAY, OBJECT -> "TEXT";
        };
    }

    @Override
    public boolean doesDataHolderExist(@NotNull String target) throws HazeException {
        if(this.closed.get()) throw new HazeException("This HazeDatabase connection is closed.");
        
        try(Connection connection = this.dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT CASE WHEN EXISTS ( SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ? ) THEN 1 ELSE 0 END AS doesExist;");
            statement.setString(1, target);
            try(ResultSet result = statement.executeQuery()) {
                if(!result.next()) throw new HazeException("Unable to tell if the table `"+target+"` exists. No response was received.");
                return result.getBoolean(1);
            }
        } catch (ClassCastException e) {
            throw new HazeCastingException(e.getMessage());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteDataHolder(@NotNull String target) throws HazeException {
        if(this.closed.get()) throw new HazeException("This HazeDatabase connection is closed.");
        
        try(Connection connection = this.dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("DROP TABLE ?;");
            statement.setString(1, target);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        System.out.println("HikariDataSource is being closed. This tends to take a while.");
        
        this.closed.set(true);
        this.dataSource.getHikariPoolMXBean().softEvictConnections();
        this.dataSource.close();
    }

    @Override
    public @Nullable Component details() {
        return null;
    }
}
