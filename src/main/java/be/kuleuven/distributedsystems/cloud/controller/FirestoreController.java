package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Seat;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import be.kuleuven.distributedsystems.cloud.entities.Train;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.print.Doc;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;


@Service
public class FirestoreController {
    private final Firestore firestore;
    private final String ourTrain = "Eurostar London";
    private final String ourTrainId = UUID.randomUUID().toString();

    @Autowired
    public FirestoreController(Firestore firestore) {
        this.firestore = firestore;
    }

    public void addBooking(Booking booking) {
        DocumentReference docRef = firestore.collection("bookingCollection").document(booking.getId());
        //extract fields from booking
        String id = booking.getId();
        String time = booking.getTime();
        List<Ticket> tickets = booking.getTickets();
        String customer = booking.getCustomer();

        //create data object with fields of the booking to store in db
        Map<String, Object> docData = new HashMap<>();
        docData.put("bookingReference", id);
        docData.put("customer", customer);
        docData.put("time", time);
        docData.put("tickets", tickets);
        //store in db
        docRef.set(docData);
    }

    // function to return all bookings from the firestore
    public List<Booking> getAllBookings() {
        CollectionReference docRef = firestore.collection("bookingCollection");
        ApiFuture<QuerySnapshot> querySnapshot = docRef.get();

        try {
            //list to hold local bookings
            List<Booking> bookings = new ArrayList<>();
            //for each document create a local booking to be returned
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()){
                Booking newBooking = document.toObject(Booking.class);
                bookings.add(newBooking);
            }
            return bookings;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // function to add the Eurostar London train to firestore
    public void addTrainInfo() {
            CollectionReference colRef = firestore.collection("OurTrain");
            Train train = getTrain("data.json");
            train.setTrainId(ourTrainId);
            train.setTrainCompany("ourTrainCompany");
            List<Seat> seats = getSeats("data.json");

            // create a new train
            DocumentReference trainDocRef = colRef.document(train.getName());
            ApiFuture<WriteResult> result = trainDocRef.set(train, SetOptions.merge());

            // group seats by trainid
            Map<String, List<Seat>> seatsGrouped = groupSeats(seats);

            // add seats to the firestore
            for (Map.Entry<String, List<Seat>> entry : seatsGrouped.entrySet()) {
                List<Seat> seatsGroupedById = entry.getValue();
                seatsGroupedById.sort(Comparator.comparing(Seat::getName));
                CollectionReference seatRef = trainDocRef.collection(entry.getKey());

                for (Seat seat : seatsGroupedById) {
                    seatRef.add(seat);
                }
            }

            CollectionReference timesRef = trainDocRef.collection("times");
            List<String> trainTimes = getTrainTimes(ourTrain);
            System.out.println("trainTimes in adddata" + trainTimes);
            for (String time : trainTimes) {
                Map<String, Object> timeData = new HashMap<>();
                timeData.put("testdata", "value");
                ApiFuture<WriteResult> writeResult = timesRef.document(time).set(timeData);
            }
    }

    // function that returns the list of train times given a trainId and train company name
    public List<String> getTrainTimesFromId(String trainName, String trainId) {
        CollectionReference colRef = firestore.collection("OurTrain");
        DocumentReference trainDocRef = colRef.document(trainName);

        try {
            DocumentSnapshot docSnapshot = trainDocRef.get().get();
            if (docSnapshot.exists()) {
                CollectionReference timesRef = trainDocRef.collection("times");
                ApiFuture<QuerySnapshot> querySnapshot = timesRef.get();
                List<String> timesList = new ArrayList<>();
                System.out.println(timesList);

                for (QueryDocumentSnapshot document : querySnapshot.get().getDocuments()) {
                    timesList.add(document.getId());
                }
                System.out.println(timesList);

                return timesList;
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    // function to group seats by time, seat data is stored by train time in firestore
    private Map<String, List<Seat>> groupSeats(List<Seat> seats) {
        Map<String, List<Seat>> seatsGrouped = new HashMap<>();
        for (Seat seat : seats) {
            seat.setTrainId(ourTrainId);
            String trainId = seat.getTrainId();
            seat.setTrainCompany(ourTrain);
            seat.setSeatId(UUID.randomUUID().toString());
            seatsGrouped.computeIfAbsent(trainId, k -> new ArrayList<>()).add(seat);
        }
        return seatsGrouped;
    }

    // function to get seats from json file
    public Train getTrain(String fileName) {
        JsonObject jsonObject = getJsonObject("data.json");
        JsonArray trainsArray = jsonObject.getAsJsonArray("trains");
        JsonObject ourTrain = trainsArray.get(0).getAsJsonObject();

        Gson gson = new Gson();
        return gson.fromJson(ourTrain, Train.class);
    }

    // function to extract seats from json file
    public List<Seat> getSeats(String fileName) {
        JsonObject jsonObject = getJsonObject("data.json");
        JsonArray trainsArray = jsonObject.getAsJsonArray("trains");

        Gson gson = new Gson();
        List<Seat> seats = new ArrayList<>();

        for (JsonElement trainElement : trainsArray) {
            JsonObject trainObject = trainElement.getAsJsonObject();
            JsonArray seatsArray = trainObject.getAsJsonArray("seats");

            for (JsonElement seatElement : seatsArray) {
                Seat seat = gson.fromJson(seatElement, Seat.class);
                seats.add(seat);
            }
        }
        seats.sort(Comparator.comparing(Seat::getName));
        return seats;
    }

    // function to get a JsonObject from .json file
    private JsonObject getJsonObject(String fileName) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        Gson gson = new Gson();
        return gson.fromJson(new InputStreamReader(inputStream), JsonObject.class);
    }

    // function to check if Eurostar London train data has been put into firestore
    public boolean dataInitialised() {
        DocumentReference docRef = firestore.collection("OurTrain").document("Eurostar London");
        try {
            //get snapshot of the document, returning false if it doesn't exist
            DocumentSnapshot docSnapShot = docRef.get().get();
            if (docSnapShot.exists()) {
                return true;
            } else {
                return false;
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    //DONT THINK WE NEED TO USE BUT KEEPING FOR THE MOMENT
    public Booking getBooking(String bookingId) {
        DocumentReference docRef = firestore.collection("bookingCollection").document(bookingId);
        ApiFuture<DocumentSnapshot> future = docRef.get();

        try {
            //get document from the db
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                //extract data from different fields of the document
                List<Ticket> ticketList = (List<Ticket>) document.get("tickets");
                String bookingRef = (String) document.get("bookingId");
                String timeString = (String) document.get("time");
                String time = String.valueOf(LocalDateTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                String customer = (String) document.get("customer");

                return new Booking(bookingRef, time, ticketList, customer);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Error when receiving booking");
    }

    // function to return our train from firestore
    public Train getTrainByName(String name) {
        CollectionReference colRef = firestore.collection("OurTrain");
        DocumentReference trainDocRef = colRef.document(name);

        try {
            DocumentSnapshot docSnapshot = trainDocRef.get().get();
            if (docSnapshot.exists()) {
                Train train = docSnapshot.toObject(Train.class);
                System.out.println(train);
                return train;
            } else {
                System.out.println("train not in firestore");
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    // function that takes the name of a train and returns all of its times, returns a list of unique train times
    // to be used when initialising data in the firebase, nowhere else
    public List<String> getTrainTimes(String trainName) {
        CollectionReference colRef = firestore.collection("OurTrain");
        DocumentReference trainDocRef = colRef.document(trainName);

        try {
            DocumentSnapshot docSnapshot = trainDocRef.get().get();
            if (docSnapshot.exists()) {
                Iterable<CollectionReference> colTrains = trainDocRef.listCollections();
                // only store each unique time once
                Set<String> uniqueTimes = new HashSet<>();

                for (CollectionReference collection : colTrains) {
                    ApiFuture<QuerySnapshot> querySnapshot = collection.get();
                    QuerySnapshot snapshots = querySnapshot.get();

                    for (QueryDocumentSnapshot document : snapshots) {
                        String time = document.getString("time");
                        uniqueTimes.add(time);
                    }
                }
                System.out.println("unique" + uniqueTimes);
                return new ArrayList<>(uniqueTimes);
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    // function that takes the name of a train and its time and returns all its seats
    public List<Seat> getSeatsFromTrainId(String trainName, String time, String trainId) {
        CollectionReference colRef = firestore.collection("OurTrain");
        DocumentReference trainDocRef = colRef.document(trainName);
        CollectionReference colTimeRef = trainDocRef.collection(trainId);

        List<Seat> seats = new ArrayList<>();

        try {
            Query query = colTimeRef.whereEqualTo("time", time).orderBy("name");
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();

            for (QueryDocumentSnapshot document : documents) {
                Seat seat = document.toObject(Seat.class);
                seats.add(seat);
            }
            System.out.println(seats);

        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return seats;
    }

    public Seat getSeatFromId(String trainName, String id) {
        CollectionReference colRef = firestore.collection("OurTrain");
        DocumentReference trainDocRef = colRef.document(trainName);

    return null;
    }

}