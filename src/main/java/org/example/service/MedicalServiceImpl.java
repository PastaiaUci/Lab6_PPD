package org.example.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.example.Config;
import org.example.model.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MedicalServiceImpl {

    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter verifyFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String PROGRAM_OUTPUT_FILENAME = "program_data.txt";
    private static final String PAYMENT_OUTPUT_FILENAME = "payment_data.txt";
    private static final String VERIFICATION_FILENAME = "verify_data.txt";

    private List<Interval> intervals = new ArrayList<>();

    private final Config config;
    private final Lock programLock = new ReentrantLock();
    private final Lock paymentLock = new ReentrantLock();
    private final Lock verificationLock = new ReentrantLock();

    public MedicalServiceImpl(Config config) throws IOException {
        this.config = config;
        clearFiles();
    }

    private void clearFiles() throws IOException {
        var files = List.of(PROGRAM_OUTPUT_FILENAME, PAYMENT_OUTPUT_FILENAME, VERIFICATION_FILENAME);
        for (var filename : files) {
            var file = new File(filename);
            if (file.exists()) {
                Files.write(Path.of(filename), new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
            }
        }
    }

    public ProgramResponse processProgramRequest(ProgramRequest request) {
        var maxClientsPerTreatment = config.getMaxClientsPerTreatment()[request.getLocation()][request.getTreatmentType()];
        var minutesStart = request.getTreatmentTime().getHour() * 60 + request.getTreatmentTime().getMinute();
        var duration = config.getTreatmentsDuration()[request.getTreatmentType()];

        try {
            programLock.lock();
            intervals.add(new Interval(request.getCnp(), request.getLocation(), request.getTreatmentType(), minutesStart, minutesStart + duration));
            var maxOverlap = maximumOverlappingIntervals(getAllIntervalsWithLocationAndTreatmentType(intervals, request.getLocation(), request.getTreatmentType()));
            if (maxOverlap > maxClientsPerTreatment) {
                intervals.remove(intervals.size() - 1);
                return new ProgramResponse(ProgramStatus.FAIL);
            }
            saveProgramRequest(request);
            return new ProgramResponse(ProgramStatus.SUCCESS);
        } finally {
            programLock.unlock();
        }
    }


    public Response processPayment(ProgramRequest lastProgramRequest) {
        var sum = config.getTreatmentsCost()[lastProgramRequest.getTreatmentType()];
        try {
            paymentLock.lock();
            savePayment(new Payment(
                            LocalDate.now(),
                            lastProgramRequest.getCnp(),
                            sum,
                            lastProgramRequest.getLocation(),
                            lastProgramRequest.getTreatmentType(),
                            lastProgramRequest.getTreatmentTime()
                    )
            );
            return new OkResponse();
        } finally {
            paymentLock.unlock();
        }
    }

    public Response cancelPayment(ProgramRequest lastProgramRequest) {
        try {
            programLock.lock();
            paymentLock.lock();
            int indexToDelete = -1;
            for (int i = 0; i < intervals.size(); i++) {
                var interval = intervals.get(i);
                if (interval.getTreatmentType() == lastProgramRequest.getTreatmentType() &&
                        interval.getCnp().equals(lastProgramRequest.getCnp()) &&
                        interval.getLocation() == lastProgramRequest.getLocation() &&
                        lastProgramRequest.getTreatmentTime().equals(getHourFromMinutes(interval.getMinutesStart()))) {
                    indexToDelete = i;
                    break;
                }
            }
            intervals.remove(indexToDelete);

            deleteProgramFromFile(lastProgramRequest);
            var sum = config.getTreatmentsCost()[lastProgramRequest.getTreatmentType()];
            savePayment(new Payment(
                            LocalDate.now(),
                            lastProgramRequest.getCnp(),
                            (-1) * sum,
                            lastProgramRequest.getLocation(),
                            lastProgramRequest.getTreatmentType(),
                            lastProgramRequest.getTreatmentTime()
                    )
            );
        } finally {
            paymentLock.unlock();
            programLock.unlock();
        }

        return new OkResponse();
    }

    public void verify() {
        System.out.println("VERIFYING.....");
        programLock.lock();
        paymentLock.lock();
        List<Interval> programIntervals = new ArrayList<>(intervals.size());
        programIntervals.addAll(Collections.nCopies(intervals.size(), null));
        Collections.copy(programIntervals, intervals);
        var payments = getAllPayments();
        paymentLock.unlock();
        programLock.unlock();
        // Compute total price for each location
        var locationPrices = new HashMap<Integer, Integer>();
        for (var payment : payments) {
            locationPrices.merge(payment.getLocation(), payment.getSum(), Integer::sum);
        }

        // Compute unpaid program requests;
        var unpaidLocations = new HashMap<Integer, ArrayList<Interval>>();
        var intervalsForLocation = new HashMap<Integer, ArrayList<Interval>>();
        for (var interval : programIntervals) {
            var paymentOpt = payments.stream()
                    .filter(payment -> payment.getCnp().equals(interval.getCnp()) &&
                            payment.getLocation() == interval.getLocation() &&
                            payment.getTreatmentType() == interval.getTreatmentType() &&
                            payment.getTreatmentTime().equals(getHourFromMinutes(interval.getMinutesStart()))
                    )
                    .findAny();
            if (intervalsForLocation.get(interval.getLocation()) == null) {
                var newElem = new ArrayList<Interval>();
                newElem.add(interval);
                intervalsForLocation.put(interval.getLocation(), newElem);
            } else {
                intervalsForLocation.get(interval.getLocation()).add(interval);
            }
            if (paymentOpt.isEmpty()) {
                if (unpaidLocations.get(interval.getLocation()) == null) {
                    var newElem = new ArrayList<Interval>();
                    newElem.add(interval);
                    unpaidLocations.put(interval.getLocation(), newElem);
                } else {
                    unpaidLocations.get(interval.getLocation()).add(interval);
                }
            }
        }
        verifyCorrectData(locationPrices, unpaidLocations, intervalsForLocation);
        writeVerificationToFile(locationPrices, unpaidLocations, intervalsForLocation);
    }

    private void verifyCorrectData(
            HashMap<Integer, Integer> locationPrices,
            HashMap<Integer, ArrayList<Interval>> unpaidLocations,
            HashMap<Integer, ArrayList<Interval>> intervalsForLocation) {

        for (int i = 0; i < config.getNumberOfLocations(); i++) {
            if (intervalsForLocation.get(i) == null) {
                assert (locationPrices.get(i) == null);
                assert (unpaidLocations.get(i) == null);
                continue;
            }
            int sum = 0;
            if (intervalsForLocation.get(i) != null) {
                sum = intervalsForLocation.get(i)
                        .stream()
                        .map(interval -> config.getTreatmentsCost()[interval.getTreatmentType()])
                        .reduce(0, Integer::sum);
            }
            int unpaid = 0;
            if (unpaidLocations.get(i) != null) {
                unpaid = unpaidLocations.get(i)
                        .stream()
                        .map(interval -> config.getTreatmentsCost()[interval.getTreatmentType()])
                        .reduce(0, Integer::sum);
            }
            assert sum == 0 || (locationPrices.get(i) == (sum - unpaid));
        }
    }

    private void writeVerificationToFile(Map<Integer, Integer> locationPrice,
                                         Map<Integer, ArrayList<Interval>> unpaidLocations,
                                         Map<Integer, ArrayList<Interval>> intervalsForLocation) {
        verificationLock.lock();
        try (var writer = new BufferedWriter(new FileWriter(VERIFICATION_FILENAME, true))) {
            if (intervalsForLocation.isEmpty()) {
                return;
            }
            writer.write(String.format("%s\n", LocalDateTime.now().format(verifyFormat)));
            for (int location = 0; location < config.getNumberOfLocations(); location++) {
                writer.write(String.format("Location: %s ; Total Sold: %s\n", location, locationPrice.getOrDefault(location, 0)));
                var currentUnpaidLocations = unpaidLocations.get(location);
                if (currentUnpaidLocations == null || currentUnpaidLocations.isEmpty()) {
                    writer.write("No unpaid programming\n");
                } else {
                    writer.write("Unpaid programming list: ");
                    for (var unpaidLocation : currentUnpaidLocations) {
                        var hour = getHourFromMinutes(unpaidLocation.getMinutesStart());
                        writer.write(String.format("[cnp: %s; location: %s; treatment: %s; time: %s:%s], ",
                                        unpaidLocation.getCnp(),
                                        unpaidLocation.getLocation(),
                                        unpaidLocation.getTreatmentType(),
                                        hour.getHour(),
                                        hour.getMinute()
                                )
                        );
                    }
                    writer.write("\n");
                }
                writer.write("\n");
                for (int programmingType = 0; programmingType < config.getNumberOfTreatments(); programmingType++) {
                    if (intervalsForLocation.get(location) == null) {
                        continue;
                    }
                    var intervalsToCheck = getAllIntervalsWithLocationAndTreatmentType(intervalsForLocation.get(location), location, programmingType);
                    if (intervalsToCheck.isEmpty()) {
                        continue;
                    }
                    var maximumAdmitted = maximumOverlappingIntervals(intervalsToCheck);
                    writer.write(String.format("Treatment type: %s ; Max admitted: %s ; ", programmingType, maximumAdmitted));
                    var timeIntervals = getAllTimeIntervals(intervalsToCheck);
                    for (var timeInterval : timeIntervals) {
                        writer.write(String.format("[Interval: %s:%s - %s:%s ; Admitted: %s], ",
                                        timeInterval.getStart().getHour(),
                                        timeInterval.getStart().getMinute(),
                                        timeInterval.getEnd().getHour(),
                                        timeInterval.getEnd().getMinute(),
                                        timeInterval.getAdmitted()
                                )
                        );
                    }
                }
                writer.write("\n");
            }
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            verificationLock.unlock();
        }
    }

    private Hour getHourFromMinutes(Integer minutesToConvert) {
        var hour = minutesToConvert / 60;
        var minutes = minutesToConvert % 60;
        return new Hour(hour, minutes);
    }

    private List<Interval> getAllIntervalsWithLocationAndTreatmentType(List<Interval> intervalsToFilter,
                                                                       int location, int treatmentType) {
        return intervalsToFilter
                .stream()
                .filter(i -> (i.getLocation() == location && i.getTreatmentType() == treatmentType))
                .toList();
    }

    private void deleteProgramFromFile(ProgramRequest request) {
        try {
            // input the (modified) file content to the StringBuffer "input"
            BufferedReader file = new BufferedReader(new FileReader(PROGRAM_OUTPUT_FILENAME));
            StringBuilder inputBuffer = new StringBuilder();
            String line;

            while ((line = file.readLine()) != null) {
                if (getRequestFromLine(line).equals(request)) {
                    continue;
                }
                inputBuffer.append(line);
                inputBuffer.append('\n');
            }
            file.close();

            // write the new string with the replaced line OVER the same file
            FileOutputStream fileOut = new FileOutputStream(PROGRAM_OUTPUT_FILENAME);
            fileOut.write(inputBuffer.toString().getBytes());
            fileOut.flush();
            fileOut.close();

        } catch (Exception e) {
            System.out.println("Problem reading file.");
        }
    }

    private void saveProgramRequest(ProgramRequest request) {
        try (var writer = new BufferedWriter(new FileWriter(PROGRAM_OUTPUT_FILENAME, true))) {
            writer.write(String.format("%s;%s;%s;%s;%s;%s;%s:%s\n",
                    request.getName(),
                    request.getCnp(),
                    LocalDate.now().format(dateFormat),
                    request.getLocation(),
                    request.getTreatmentType(),
                    LocalDate.now().format(dateFormat),
                    request.getTreatmentTime().getHour(),
                    request.getTreatmentTime().getMinute()
            ));
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ProgramRequest getRequestFromLine(String line) {
        var parts = line.split(";");
        var name = parts[0];
        var cnp = parts[1];
        var location = Integer.parseInt(parts[3]);
        var treatmentType = Integer.parseInt(parts[4]);
        var hourParts = parts[6].split(":");
        var hour = Integer.parseInt(hourParts[0]);
        var minutes = Integer.parseInt(hourParts[1]);
        return new ProgramRequest(name, cnp, location, treatmentType, new Hour(hour, minutes));
    }

    private List<Payment> getAllPayments() {
        try (var reader = new BufferedReader(new FileReader(PAYMENT_OUTPUT_FILENAME))) {
            List<Payment> payments = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                payments.add(getPaymentFromLine(line));
            }
            return payments;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Payment getPaymentFromLine(String line) {
        var parts = line.split(";");
        var cnp = parts[1];
        var sum = Integer.parseInt(parts[2]);
        var location = Integer.parseInt(parts[3]);
        var treatmentType = Integer.parseInt(parts[4]);
        var hourParts = parts[5].split(":");
        var hour = Integer.parseInt(hourParts[0]);
        var minutes = Integer.parseInt(hourParts[1]);
        return new Payment(null, cnp, sum, location, treatmentType, new Hour(hour, minutes));
    }

    private void savePayment(Payment payment) {
        try (var writer = new BufferedWriter(new FileWriter(PAYMENT_OUTPUT_FILENAME, true))) {
            writer.write(String.format("%s;%s;%s;%s;%s;%s:%s\n",
                    payment.getDate().format(dateFormat),
                    payment.getCnp(),
                    payment.getSum(),
                    payment.getLocation(),
                    payment.getTreatmentType(),
                    payment.getTreatmentTime().getHour(),
                    payment.getTreatmentTime().getMinute()
            ));
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<TimeInterval> getAllTimeIntervals(List<Interval> intervals) {
        var count = getIntervalOverlapArray(intervals);
        var timeIntervals = new ArrayList<TimeInterval>();
        for (int i = 0; i < count.length; i++) {
            if (count[i] != 0) {
                var start = i;
                while (i < count.length && count[start] == count[i]) {
                    i++;
                }
                var end = i - 1;
                timeIntervals.add(new TimeInterval(getHourFromMinutes(start), getHourFromMinutes(end), count[start]));
            }
        }
        return timeIntervals;
    }

    private int[] getIntervalOverlapArray(List<Interval> intervals) {
        // Find the time when the last interval ends
        var endTime = intervals
                .stream()
                .map(Interval::getMinutesEnd)
                .toList();
        var startTime = intervals
                .stream()
                .map(Interval::getMinutesStart)
                .toList();
        int maxEndTime = endTime.stream().max(Comparator.naturalOrder()).get();

        int[] count = new int[maxEndTime + 2];

        // Fill the count array range count using the array index to store time
        for (int i = 0; i < endTime.size(); i++) {
            for (int j = startTime.get(i); j <= endTime.get(i); j++) {
                count[j]++;
            }
        }
        return count;
    }

    private Integer maximumOverlappingIntervals(List<Interval> intervals) {
        int max_event_tm = 0;
        var count = getIntervalOverlapArray(intervals);
        for (int i = 0; i < count.length; i++) {
            if (count[max_event_tm] < count[i]) {
                max_event_tm = i;
            }
        }

        return count[max_event_tm];
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private static class Payment {
        private LocalDate date;
        private String cnp;
        private Integer sum;
        private Integer location;
        private Integer treatmentType;
        private Hour treatmentTime;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private static class TimeInterval {
        private Hour start;
        private Hour end;
        private int admitted;
    }

    @AllArgsConstructor
    @Getter
    @Setter
    private static class Interval {
        private String cnp;
        private int location;
        private int treatmentType;
        private int minutesStart;
        private int minutesEnd;
    }
}
