package org.example.client;

import lombok.RequiredArgsConstructor;
import org.example.Config;
import org.example.model.Hour;
import org.example.model.ProgramRequest;
import org.example.model.ProgramStatus;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class Client {
    private final String host;
    private final int port;
    private final int rate;
    private final TimeUnit timeUnit;
    private final int numberOfClients;
    private final String clientsName;
    private final Config config;
    private AtomicInteger currentClientCount = new AtomicInteger();
    private ScheduledExecutorService executorService;

    private static ProgramRequest getRandomProgramRequest(String name, String cnp, Config config) {
        var random = new Random();
        var location = random.nextInt(config.getNumberOfLocations());
        var treatment = random.nextInt(config.getNumberOfTreatments());
        int hour = random.nextInt(18 - 10) + 10;
        int minutes = random.nextInt(60);
        // TODO: change me
        return new ProgramRequest(name, cnp, location, treatment, new Hour(hour, minutes));
    }

    public void startClients() {
        currentClientCount.set(numberOfClients);
        executorService = Executors.newScheduledThreadPool(numberOfClients);
        for (int i = 1; i <= numberOfClients; i++) {
            var name = clientsName + i;
            var cnp = "cnp" + i;
            var clientProxy = new ClientProxy(host, port, currentClientCount, name);
            executorService.scheduleAtFixedRate(() -> {
                var response = clientProxy.sendProgramRequest(getRandomProgramRequest(name, cnp, config));
                if (response.getStatus() == ProgramStatus.SUCCESS) {
                    var shouldCancel = new Random().nextBoolean();
                    try {
                        Thread.sleep(500);
                    } catch (Exception ignored) {
                    }
                    clientProxy.sendPaymentRequest();
                    if (shouldCancel) {
                        clientProxy.sendCancelRequest();
                    }
                }
            }, 1, rate, timeUnit);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> executorService.shutdownNow()));
    }
}
