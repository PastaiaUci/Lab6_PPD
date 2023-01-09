package org.example.server;

import lombok.RequiredArgsConstructor;
import org.example.service.MedicalServiceImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


@RequiredArgsConstructor
public class Server {

    private final int port;
    private final int numberOfThreads;
    private final MedicalServiceImpl medicalService;
    private final int verificationDuration;
    private final TimeUnit verificationTimeUnit;
    private ExecutorService executor;
    private ScheduledExecutorService scheduledVerification;

    private ServerSocket server = null;
    private AtomicBoolean shouldEnd = new AtomicBoolean();

    public void start() {
        scheduledVerification = Executors.newSingleThreadScheduledExecutor();
        scheduledVerification.scheduleAtFixedRate(
                () -> {
                    try {
                        medicalService.verify();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                },
                0,
                verificationDuration,
                verificationTimeUnit
        );
        executor = Executors.newFixedThreadPool(numberOfThreads);

        try {
            server = new ServerSocket(port);
            while (!shouldEnd.get()) {
                System.out.println("Waiting for clients ...");
                Socket client = server.accept();
                System.out.println("Client connected ...");
                executor.submit(new Worker(client, medicalService, shouldEnd));
            }
        } catch (IOException e) {
            throw new RuntimeException("Starting org.example.server errror ", e);
        } finally {
            System.out.println("Shutting down executors from org.example.server");
            stop();
        }
    }

    public void stop() {
        try {
            shouldEnd.set(true);
            executor.shutdownNow();
            scheduledVerification.shutdownNow();
            server.close();
            System.exit(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
