import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        String directory = null;
        if ((args.length == 2) && (args[0].equalsIgnoreCase("--directory"))) {
            directory = args[1];
        }
        
        try (ServerSocket serverSocket = new ServerSocket(4221)) {
            serverSocket.setReuseAddress(true);
            System.out.println("Server is running on port 4221");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                final String finalDirectory = directory;
                new Thread(() -> handleRequest(clientSocket, finalDirectory)).start();
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static void handleRequest(Socket clientSocket, String directory) {
        try (InputStream input = clientSocket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input));
             OutputStream output = clientSocket.getOutputStream()) {

            String requestLine = reader.readLine();
            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0];
            String path = requestParts[1].substring(1); // remove leading '/'

            if (method.equals("GET")) {
                handleGetRequest(path, directory, output);
            } else if (method.equals("POST")) {
                handlePostRequest(path, directory, reader, output);
            } else {
                output.write("HTTP/1.1 405 Method Not Allowed\r\n\r\n".getBytes());
            }
            output.flush();

        } catch (IOException e) {
            System.out.println("IOException while handling request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("IOException on socket close: " + e.getMessage());
            }
        }
    }

    private static void handleGetRequest(String path, String directory, OutputStream output) throws IOException {
        Path filePath = Paths.get(directory, path);
        if (Files.exists(filePath)) {
            byte[] fileBytes = Files.readAllBytes(filePath);
            String response = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " +
                    fileBytes.length + "\r\n\r\n";
            output.write(response.getBytes());
            output.write(fileBytes);
        } else {
            output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
        }
    }

    private static void handlePostRequest(String path, String directory, BufferedReader reader, OutputStream output) throws IOException {
        String contentLengthHeader = null;
        String line;

        // Read headers to get Content-Length
        while (!(line = reader.readLine()).isEmpty()) {
            if (line.startsWith("Content-Length:")) {
                contentLengthHeader = line.split(": ")[1];
            }
        }

        if (contentLengthHeader != null) {
            int contentLength = Integer.parseInt(contentLengthHeader);
            char[] body = new char[contentLength];
            int bytesRead = reader.read(body, 0, contentLength);
            
            if (bytesRead == contentLength) {
                String requestBody = new String(body);
                
                // Create file
                Path filePath = Paths.get(directory, path);
                Files.write(filePath, requestBody.getBytes());
                output.write("HTTP/1.1 201 Created\r\n\r\n".getBytes());
            } else {
                output.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
            }
        } else {
            output.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
        }
    }

}
