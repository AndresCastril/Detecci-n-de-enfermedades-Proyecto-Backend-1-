package org.genomics;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;

public class TCPServer{
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

            //Inicializar contador de pacientes según CSV
            initPatientCounter();

            System.out.println("Server started on port: " + serverPort);
            loadDiseaseDatabase(); // cargar enfermedades

            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //leer el último ID de patients.csv
    private void initPatientCounter() {
        File csvFile = new File("data/patients.csv");
        if (!csvFile.exists()) {
            patientCounter.set(1);
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String header = br.readLine(); // saltar encabezado
            String line;
            int lastId = 0;

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length > 0) {
                    try {
                        int id = Integer.parseInt(parts[0].trim());
                        if (id > lastId) lastId = id;
                    } catch (NumberFormatException ignored) {}
                }
            }

            patientCounter.set(lastId + 1);
            System.out.println("Patient counter initialized to: " + patientCounter.get());

        } catch (IOException e) {
            e.printStackTrace();
            patientCounter.set(1); // fallback
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
                out.println("patient_id:" + newId); // ojo: mejor usar println que print
                savePatientToCSV(newId, request.toString());
                String patientSequence = extractFastaSequence(request.toString());
                detectDiseases(newId, patientSequence);
                System.out.println("Assigned patient_id: " + newId + "\n");

            } else if (request.toString().startsWith("GET_PATIENT")) {
                String[] parts = request.toString().split("\n");
                String idLine = parts[0]; // GET_PATIENT <id>
                String[] tokens = idLine.split(" ");
                if (tokens.length == 2) {
                    int patientId = Integer.parseInt(tokens[1]);
                    String patientData = getPatientById(patientId);
                    if (patientData != null) {
                        out.println("SUCCESS");
                        out.println(patientData);
                    } else {
                        out.println("ERROR");
                        out.println("message:Patient not found");
                    }
                } else {
                    out.println("ERROR");
                    out.println("message:Invalid GET_PATIENT format");
                }

            } else if (request.toString().startsWith("DELETE_PATIENT")) {
                // Separa por cualquier espacio o salto de línea extra
                String[] parts = request.toString().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        int patientId = Integer.parseInt(parts[1].trim());
                        boolean deleted = deletePatient(patientId);
                        if (deleted) {
                            out.println("SUCCESS");
                            out.println("message:Patient " + patientId + " deleted (is_active=false)");
                        } else {
                            out.println("ERROR");
                            out.println("message:Patient not found");
                        }
                    } catch (NumberFormatException e) {
                        out.println("ERROR");
                        out.println("message:Invalid patient ID");
                    }
                } else {
                    out.println("ERROR");
                    out.println("message:Invalid DELETE_PATIENT format");
                }
            }

            else if (request.toString().startsWith("UPDATE_PATIENT")) {
                String[] parts = request.toString().split("\n");
                String idLine = parts[0]; // UPDATE_PATIENT <id>
                String[] tokens = idLine.split(" ");
                if (tokens.length == 2) {
                    try {
                        int patientId = Integer.parseInt(tokens[1]);
                        boolean updated = updatePatient(patientId, request.toString());
                        if (updated) {
                            out.println("SUCCESS");
                            out.println("message:Patient " + patientId + " updated");
                        } else {
                            out.println("ERROR");
                            out.println("message:Patient not found or inactive");
                        }
                    } catch (NumberFormatException e) {
                        out.println("ERROR");
                        out.println("message:Invalid patient ID");
                    }
                } else {
                    out.println("ERROR");
                    out.println("message:Invalid UPDATE_PATIENT format");
                }
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
                pw.println("patient_id,full_name,document_id,age,sex,contact_email,registration_date,clinical_notes,checksum_fasta,file_size_bytes,is_active");
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


            pw.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,true%n",
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

    private String getPatientById(int patientId) {
        File csvFile = new File("data/patients.csv");
        if (!csvFile.exists()) return null;

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String headerLine = br.readLine();
            if (headerLine == null) return null;

            // Mapa de nombres de columna a índice
            String[] headers = headerLine.split(",", -1);
            int idIdx = -1;
            int activeIdx = -1;
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim().toLowerCase();
                if (h.equals("patient_id")) idIdx = i;
                if (h.equals("is_active")) activeIdx = i;
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1); // -1 para conservar columnas vacías al final
                if (idIdx >= 0 && parts.length > idIdx && parts[idIdx].trim().equals(String.valueOf(patientId))) {
                    // si existe columna is_active y está en "false", tratamos como no encontrado
                    if (activeIdx >= 0) {
                        String activeVal = (parts.length > activeIdx) ? parts[activeIdx].trim().toLowerCase() : "";
                        if (activeVal.equals("false") || activeVal.equals("0") || activeVal.equals("no")) {
                            return null; // paciente eliminado lógicamente -> no devolver
                        }
                    }
                    // Si no hay columna is_active, asumimos activo y devolvemos la línea
                    return line;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    private boolean deletePatient(int patientId) {
        File inputFile = new File("data/patients.csv");
        File tempFile = new File("data/patients_temp.csv");

        if (!inputFile.exists()) return false;

        boolean deleted = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {

            String header = reader.readLine();
            if (header == null) return false;

            // Escribimos encabezado (si no tiene is_active, lo agregamos)
            String[] headers = header.split(",", -1);
            int activeIdx = -1;
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].trim().equalsIgnoreCase("is_active")) {
                    activeIdx = i;
                    break;
                }
            }
            if (activeIdx == -1) {
                // agregamos columna is_active al header
                writer.println(header + ",is_active");
                activeIdx = headers.length; // será la última columna nueva
            } else {
                writer.println(header);
            }

            // Reescribimos filas:
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                String idStr = (parts.length > 0) ? parts[0].trim() : "";
                if (idStr.equals(String.valueOf(patientId))) {
                    // Marca como false en la columna is_active
                    if (parts.length > activeIdx) {
                        // reemplaza valor de la columna is_active
                        parts[activeIdx] = "false";
                    } else {
                        // debe ampliar la longitud del array (conservando columnas existentes)
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length; i++) {
                            if (i > 0) sb.append(",");
                            sb.append(parts[i]);
                        }
                        // añadir comas faltantes hasta activeIdx
                        for (int i = parts.length; i < activeIdx; i++) sb.append(",");
                        sb.append(",false"); // append columna is_active=false
                        writer.println(sb.toString().replace(",,", ",")); // escribimos la fila ampliada
                        deleted = true;
                        continue;
                    }
                    // reconstruir linea con partes modificadas
                    StringBuilder outLine = new StringBuilder();
                    for (int i = 0; i < parts.length; i++) {
                        if (i > 0) outLine.append(",");
                        outLine.append(parts[i]);
                    }
                    writer.println(outLine.toString());
                    deleted = true;
                } else {
                    // fila sin modificar
                    writer.println(line);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        // reemplazar archivo original por el temporal
        if (!inputFile.delete()) {
            System.err.println("No se pudo borrar patients.csv original");
            return false;
        }
        if (!tempFile.renameTo(inputFile)) {
            System.err.println("No se pudo renombrar el archivo temporal");
            return false;
        }

        return deleted;
    }



    private boolean updatePatient(int patientId, String request) {
        File inputFile = new File("data/patients.csv");
        File tempFile = new File("data/patients_temp.csv");

        if (!inputFile.exists()) return false;

        boolean updated = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {

            String header = reader.readLine();
            if (header == null) return false;
            writer.println(header);

            String[] lines = request.split("\n");
            String fullName = "", documentId = "", age = "", sex = "", email = "",
                    regDate = "", notes = "", checksum = "", fileSize = "";

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

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts[0].trim().equals(String.valueOf(patientId)) &&
                        parts[parts.length - 1].equalsIgnoreCase("true")) {

                    writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,true%n",
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
                    updated = true;
                } else {
                    writer.println(line);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (!inputFile.delete()) return false;
        if (!tempFile.renameTo(inputFile)) return false;

        return updated;
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
