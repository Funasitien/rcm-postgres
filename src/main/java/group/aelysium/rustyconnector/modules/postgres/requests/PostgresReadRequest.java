package group.aelysium.rustyconnector.modules.postgres.requests;

import group.aelysium.rustyconnector.modules.postgres.PostgresDatabase;
import group.aelysium.rustyconnector.modules.postgres.lib.Converter;
import group.aelysium.rustyconnector.shaded.com.google.code.gson.gson.JsonSyntaxException;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.exceptions.HazeCastingException;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.exceptions.HazeException;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.lib.DataEntry;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.lib.Filter;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.lib.KeyValue;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.requests.ReadRequest;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class PostgresReadRequest extends ReadRequest {
    public PostgresReadRequest(
            @NotNull PostgresDatabase database,
            @NotNull String target
    ) {
        super(database, target);
    }

    private <T> void execute(Consumer<ResultSet> consumer, Class<T> clazz) throws Exception {
        String query = "SELECT * FROM " + target +
            Converter.convert(this.filter) +
            Converter.convert(this.orderBy) +
            Converter.convert(this.startAt, this.endAt);

        try (
            Connection connection = ((PostgresDatabase) this.database).dataSource().getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(query)
        ) {
            if (this.filter != null) {
                this.filter.resetPointer();

                int index = 1;
                while (this.filter.next()) {
                    KeyValue<Filter.Operator, KeyValue<String, Filter.Value>> entry = this.filter.get();
                    preparedStatement.setObject(index++, entry.value().value().value());
                }
            }

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                consumer.accept(resultSet);
            }
        }
    }

    @Override
    public <T> @NotNull Set<T> execute(Class<T> clazz) throws Exception {
        try {
            Set<T> response = new HashSet<>();
            this.execute(resultSet -> {
                try {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    List<String> columnNames = new ArrayList<>(metadata.getColumnCount());
                    for (int i = 1; i <= metadata.getColumnCount(); i++)
                        columnNames.add(metadata.getColumnName(i));
                    Map<String, Object> rows = new HashMap<>(metadata.getColumnCount());

                    while (resultSet.next()) {
                        for (int i = 0; i < columnNames.size(); i++)
                            rows.put(columnNames.get(i), resultSet.getObject(i + 1));

                        try {
                            response.add(
                                PostgresDatabase.gson.fromJson(
                                    PostgresDatabase.gson.toJson(rows),
                                    clazz
                                )
                            );
                        } catch (JsonSyntaxException e) {
                            throw new HazeCastingException("Unable to deserialize the response from " + this.target + ". " + e.getMessage());
                        } finally {
                            rows.clear();
                        }
                    }
                } catch(SQLException e) {
                    throw new HazeException("Error executing read request. " + e.getMessage());
                }
            }, clazz);
            return response;
        } catch (SQLException e) {
            throw new HazeException("Error executing read request. " + e.getMessage());
        }
    }

    @NotNull
    @Override
    public <T> Set<T> executeAndFilter(@NotNull Class<T> clazz, @NotNull Function<DataEntry, Boolean> function) throws Exception {
        try {
            Set<T> response = new HashSet<>();
            this.execute(resultSet -> {
                try {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    List<String> columnNames = new ArrayList<>(metadata.getColumnCount());
                    for (int i = 1; i <= metadata.getColumnCount(); i++)
                        columnNames.add(metadata.getColumnName(i));
                    Map<String, Object> rows = new HashMap<>(metadata.getColumnCount());

                    while (resultSet.next()) {
                        for (int i = 0; i < columnNames.size(); i++)
                            rows.put(columnNames.get(i), resultSet.getObject(i + 1));

                        try(DataEntry entry = DataEntry.wrap(rows)) {
                            if(function.apply(entry))
                                response.add(
                                    PostgresDatabase.gson.fromJson(
                                        PostgresDatabase.gson.toJson(rows),
                                        clazz
                                    )
                                );
                        } catch (JsonSyntaxException e) {
                            throw new HazeCastingException("Unable to deserialize the response from " + this.target + ". " + e.getMessage());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            rows.clear();
                        }
                    }
                } catch(SQLException e) {
                    throw new HazeException("Error executing read request. " + e.getMessage());
                }
            }, clazz);
            return response;
        } catch (SQLException e) {
            throw new HazeException("Error executing read request. " + e.getMessage());
        }
    }
}
