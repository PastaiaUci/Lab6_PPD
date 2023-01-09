package org.example.server;

import org.example.model.*;
import org.example.service.MedicalServiceImpl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class Worker implements Runnable {

    private final MedicalServiceImpl medicalService;

    private final Socket connection;
    private final ObjectInputStream input;
    private final ObjectOutputStream output;
    private ProgramRequest lastSuccessfulProgramRequest;
    private final AtomicBoolean shouldEnd;

    public Worker(Socket conn, MedicalServiceImpl medicalService, AtomicBoolean shouldEnd) {
        this.connection = conn;
        this.medicalService = medicalService;
        this.shouldEnd = shouldEnd;
        try {
            output = new ObjectOutputStream(connection.getOutputStream());
            output.flush();
            input = new ObjectInputStream(connection.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (!shouldEnd.get()) {
            try {
                Object request = input.readObject();
                Object response = handleRequest((Request) request);
                if (response != null) {
                    sendResponse((Response) response);
                }
            } catch (IOException | RuntimeException | ClassNotFoundException e) {
                e.printStackTrace();
            }
/*
            try {
                System.out.println("Sleeping");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
*/
        }

        if (shouldEnd.get()) {
            System.out.println("Stopping server");
            sendResponse(new ServerStopNotification());
        }
        try {
            input.close();
            output.close();
            connection.close();
            System.out.println("Closing connection from worker");
        } catch (IOException e) {
            System.out.println("Error " + e);
        }
    }

    private Object handleRequest(Request request) {
        if (request instanceof ProgramRequest programRequest) {
            System.out.println("Handling program request " + programRequest.toString());
            var status = medicalService.processProgramRequest(programRequest);
            if (status.getStatus() == ProgramStatus.SUCCESS) {
                // TODO: use mutex here
                lastSuccessfulProgramRequest = programRequest;
            }

            return status;
        }
        if (request instanceof PayRequest) {
            System.out.println("Handling pay request " + request);
            return medicalService.processPayment(lastSuccessfulProgramRequest);
        }
        if (request instanceof CancellationRequest) {
            System.out.println("Handling cancel payment " + request);
            return medicalService.cancelPayment(lastSuccessfulProgramRequest);
        }
        return null;
    }

    private void sendResponse(Response response) {
        try {
            output.writeObject(response);
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
