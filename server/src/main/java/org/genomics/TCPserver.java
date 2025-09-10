package org.genomics;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;


public class TCPServer {
    private int serverPort;
    private static AtomicInteger patientCounter = new AtomicInteger(1); // genera patient_id
    private Map<String, String[]> diseaseDatabase = new HashMap<>(); // Base de datos de enfermedades en memoria


    public TCPServer(int serverPort) {
        this.serverPort = serverPort;
    }

    public void start() {
        try {
            // Ruta al keystore
            String keystorePath = "server/certificate/keystore.p12";
            String keystorePassword = "andres54";

            // Cargar keystore
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream keyStoreStream = new FileInputStream(keystorePath)) {
                keyStore.load(keyStoreStream, keystorePassword.toCharArray());
            }


            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword.toCharArray());

            // Crear contexto SSL
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Crear socket seguro
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(serverPort);

            System.out.println("Server started on port: " + serverPort);
            loadDiseaseDatabase(); // Llamamos a la base de datos para cargarla al iniciar el servidor

            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(SSLSocket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            StringBuilder request = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                request.append(line).append("\n");
                if (line.equals("END_FASTA")) {
                    break;
                }
            }

            System.out.println("Received message:\n" + request);

            if (request.toString().startsWith("CREATE_PATIENT")) {
                int newId = patientCounter.getAndIncrement();
                out.println("SUCCESS");
                out.print("patient_id:" + newId);
                savePatientToCSV(newId, request.toString());
                String patientSequence = extractFastaSequence(request.toString());
                detectDiseases(newId, patientSequence);
                System.out.println("Assigned patient_id: " + newId + "\n");
            } else {
                out.println("ERROR");
                out.println("message:Unknown command");
            }

            System.out.println("Response sent, closing connection.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void savePatientToCSV(int patientId, String request) {
        File csvFile = new File("data/patients.csv");
        boolean fileExists = csvFile.exists();

        try (FileWriter fw = new FileWriter(csvFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter pw = new PrintWriter(bw)) {

            if (!fileExists) {
                pw.println("patient_id,full_name,document_id,age,sex,contact_email,registration_date,clinical_notes,checksum_fasta,file_size_bytes");
            }

            String[] lines = request.split("\n");
            String fullName = "";
            String documentId = "";
            String age = "";
            String sex = "";
            String email = "";
            String regDate = "";
            String notes = "";
            String checksum = "";
            String fileSize = "";

            for (String line : lines) {
                if (line.startsWith("full_name:")) fullName = line.split(":", 2)[1];
                if (line.startsWith("document_id:")) documentId = line.split(":", 2)[1];
                if (line.startsWith("age:")) age = line.split(":", 2)[1];
                if (line.startsWith("sex:")) sex = line.split(":", 2)[1];
                if (line.startsWith("contact_email:")) email = line.split(":", 2)[1];
                if (line.startsWith("registration_date:")) regDate = line.split(":", 2)[1];
                if (line.startsWith("clinical_notes:")) notes = line.split(":", 2)[1];
                if (line.startsWith("checksum_fasta:")) checksum = line.split(":", 2)[1];
                if (line.startsWith("file_size_bytes:")) fileSize = line.split(":", 2)[1];
            }


            pw.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                    patientId,
                    safe(fullName),
                    safe(documentId),
                    safe(age),
                    safe(sex),
                    safe(email),
                    safe(regDate),
                    safe(notes),
                    safe(checksum),
                    safe(fileSize));
            System.out.println("\n" );



        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // Sebs esto era para separar campos con comas o comillas en CSV, no sirvio de mucho, eliminalo si quieres
    private String escapeCsv(String field) {
        if (field == null) return "";

        if (field.contains("\"") || field.contains(",")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim();
    }

    private void loadDiseaseDatabase() {
        try (BufferedReader br = new BufferedReader(new FileReader("data/catalog.csv"))) {
            String line = br.readLine(); // leer encabezado y saltarlo
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 4) { // disease_id,name,severity,fasta_path
                    String diseaseId = parts[0].trim();
                    String severity = parts[2].trim();
                    String fastaPath = parts[3].trim();

                    String sequence = loadFastaSequence(fastaPath);
                    if (!sequence.isEmpty()) {
                        diseaseDatabase.put(diseaseId, new String[]{sequence, severity});
                    }
                }
            }
            System.out.println("Loaded " + diseaseDatabase.size() + " diseases into database.");
        } catch (Exception e) {
            System.err.println("Error loading disease database: " + e.getMessage());
        }
    }

    private String loadFastaSequence(String fastaPath) {
        StringBuilder seq = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(fastaPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith(">")) {
                    seq.append(line.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading FASTA file: " + fastaPath + " - " + e.getMessage());
        }
        return seq.toString();
    }

    private void detectDiseases(int patientId, String patientSequence) { //Comparar secuencia genetica de paciente con base de datos
        File csvFile = new File("reports/detections.csv");
        boolean fileExists = csvFile.exists();

        try (FileWriter fw = new FileWriter(csvFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter pw = new PrintWriter(bw)) {

            if (!fileExists) {
                pw.println("patient_id,disease_id,severity,date_time,description");
            }

            for (Map.Entry<String, String[]> entry : diseaseDatabase.entrySet()) {
                String diseaseId = entry.getKey();
                String diseaseSeq = entry.getValue()[0];
                String severity = entry.getValue()[1];

                System.out.println("Comparando paciente " + patientId + " con " + diseaseId);
                System.out.println("Paciente: " + patientSequence);
                System.out.println("Enfermedad: " + diseaseSeq);

                if (patientSequence.contains(diseaseSeq)) {
                    String dateTime = java.time.LocalDateTime.now().toString();
                    String description = "Match found for " + diseaseId;

                    pw.printf("%d,%s,%s,%s,%s%n",
                            patientId, diseaseId, severity, dateTime, description);

                    System.out.println("Detection: patient " + patientId + " -> " + diseaseId);
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String extractFastaSequence(String request) {
        StringBuilder seq = new StringBuilder();
        String[] lines = request.split("\n");
        boolean fastaStarted = false;

        for (String line : lines) {
            if (line.startsWith(">")) {
                fastaStarted = true;
                continue;
            }
            if (fastaStarted && !line.equals("END_FASTA")) {
                seq.append(line.trim());
            }
        }
        return seq.toString();
    }


    public static void main(String[] args) {
        TCPServer server = new TCPServer(8080);
        server.start();
    }
}
