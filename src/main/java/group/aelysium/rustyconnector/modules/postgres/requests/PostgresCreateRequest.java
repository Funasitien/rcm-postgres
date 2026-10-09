package group.aelysium.rustyconnector.modules.postgres.requests;

import group.aelysium.rustyconnector.modules.postgres.PostgresDatabase;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.exceptions.HazeException;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.requests.CreateRequest;
import org.jetbrains.annotations.NotNull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;

public class PostgresCreateRequest extends CreateRequest {
    public PostgresCreateRequest(
            @NotNull PostgresDatabase database,
            @NotNull String target
    ) {
        super(database, target);
    }

    @Override
    public long execute() throws Exception {
        if (parameters.isEmpty()) throw new HazeException("Parameters cannot be empty for an UPSERT operation.");

        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (String column : parameters.keySet()) {
            if (!columns.isEmpty()) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(column);
            values.append("?");
        }

        String query = "INSERT INTO " + target + " (" + columns + ") VALUES (" + values + ")";
        try (
                Connection connection = ((PostgresDatabase) this.database).dataSource().getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)
        ) {
            int index = 1;
            for (Object value : parameters.values())
                preparedStatement.setObject(index++, value);

            int affectedRows = preparedStatement.executeUpdate();
            if (affectedRows < 0) throw new HazeException("No new rows were created.");
            try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                if(!generatedKeys.next()) throw new HazeException("No new rows were created.");
                return generatedKeys.getLong(1);
            }
        } catch (SQLException e) {
            throw new HazeException("Error executing Create Request. " + e.getMessage());
        }
    }
}