/**
 * Copyright 2018 Jerónimo López Bezanilla
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jerolba.benchmark;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.function.Supplier;

import com.jerolba.benchmark.shared.BenchmarkMeter;
import com.jerolba.benchmark.shared.CityBikeParser;
import com.jerolba.benchmark.shared.CityBikeReader;
import com.jerolba.benchmark.shared.ConnectionProvider;
import com.jerolba.benchmark.shared.TableHelper;
import com.jerolba.benchmark.shared.TripEntity;
import com.jerolba.benchmark.shared.TripEntityInsert;

public class JdbcBatchInsertStatementRewrite {

    public static void main(String[] args) throws IOException, SQLException {
        String properties = args[0].replace(".properties", "2.properties"); //For script support
        int batchSize = Integer.parseInt(args[1]);
        Supplier<Connection> connectionSuplier = new ConnectionProvider(properties);
        try (Connection connection = connectionSuplier.get()) {
            TableHelper.createTable(connection);
            connection.setAutoCommit(true);

            CityBikeParser<TripEntity> parser = new CityBikeParser<>(() -> new TripEntity());
            CityBikeReader<TripEntity> reader = new CityBikeReader<>("/tmp", 1_000_000, str -> parser.parse(str));
            TripEntityInsert jdbcInsert = new TripEntityInsert();

            BenchmarkMeter meter = new BenchmarkMeter(JdbcBatchInsertStatementRewrite.class, properties, batchSize);
            meter.meter(() -> reader.forEachCsvInZip(trips -> {
                    try (PreparedStatement pstmt = connection.prepareStatement(TripEntityInsert.INSERT)) {
                        int cont = 0;
                        Iterator<TripEntity> iterator = trips.iterator();
                        while (iterator.hasNext()) {
                            TripEntity entity = iterator.next();
                            jdbcInsert.setParameters(pstmt, entity);
                            pstmt.addBatch();
                            cont++;
                            if (cont % batchSize == 0) {
                                pstmt.executeBatch();
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                })
            );
        }
    }

}
