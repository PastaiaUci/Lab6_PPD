package org.example;

import org.example.client.Client;
import org.example.server.Server;
import org.example.service.MedicalServiceImpl;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    private static Integer[] getIntArrayFromLine(String line) {
        Integer[] array = new Integer[0];
        return Arrays.stream(line.split(" "))
                .map(Integer::parseInt)
                .toList()
                .toArray(array);
    }

    private static Config loadConfig() {
        try (var reader = new BufferedReader(new FileReader("config.txt"))) {

            var numberOfLocationsStr = reader.readLine();
            var numberOfLocations = Integer.parseInt(numberOfLocationsStr);

            var numberOfTreatmentsStr = reader.readLine();
            var numberOfTreatments = Integer.parseInt(numberOfTreatmentsStr);

            var treatmentsCost = getIntArrayFromLine(reader.readLine());
            var treatmentDuration = getIntArrayFromLine(reader.readLine());

            var maxClientsPerTreatment = new Integer[numberOfLocations][numberOfTreatments];

            maxClientsPerTreatment[0] = getIntArrayFromLine(reader.readLine());

            for (int i = 1; i < numberOfLocations; i++) {
                for (int j = 0; j < numberOfTreatments; j++) {
                    maxClientsPerTreatment[i][j] = maxClientsPerTreatment[0][j] * i;
                }
            }

            return new Config(numberOfLocations, numberOfTreatments, treatmentsCost, treatmentDuration, maxClientsPerTreatment);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void createServer(int p, Config config) throws IOException {
        var medService = new MedicalServiceImpl(config);
        var port = 8080;
        var server = new Server(port, p, medService, 5, TimeUnit.SECONDS);
        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(server::stop, 3, TimeUnit.MINUTES);
        server.start();
        executor.shutdownNow();
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        var type = Integer.parseInt(args[0]);
        var config = loadConfig();
        if (config == null) {
            throw new RuntimeException("Couldn't read config");
        }

        if (type == 0) {
            createServer(Integer.parseInt(args[1]), config);
        } else {
            var clientCount = Integer.parseInt(args[1]);
            var client = new Client("localhost", 8080, 2, TimeUnit.SECONDS, clientCount, "Client", config);
            client.startClients();
        }
    }
}