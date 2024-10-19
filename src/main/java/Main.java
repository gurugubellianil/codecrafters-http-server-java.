import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Main {
  public static void main(String[] args) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");
    Socket clientSocket = null;

    String dir = null;
    if((args.length == 2) && args[0].equals("--directory"))
    {
      dir = args[1];
    }

    try (ServerSocket serverSocket = new ServerSocket(4221)) {
      while(true) {
        serverSocket.setReuseAddress(true);
        clientSocket = serverSocket.accept();
        sockThread Tsock = new sockThread(clientSocket, dir);
        Tsock.start();
      }


    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
}

class sockThread extends Thread {
  private Socket sock;
  private String dir;
  public sockThread(Socket sock, String dir) {
    this.sock = sock;
    this.dir = dir;
  }

  @Override
  public void run() {
    try {
      InputStream in = sock.getInputStream();
      BufferedReader bif = new BufferedReader(new InputStreamReader(in, "UTF-8"));

      String inr;
      List<String> inp = new ArrayList<String>();
      while ((inr = bif.readLine()) != null && !inr.isEmpty()) {
        inp.add(inr);
      }

      String[] re = inp.get(0).split(" ");

      if (re[1].equals("/")) {
        sendCode(sock, "HTTP/1.1 200 OK\r\n\r\n");
      }
      if (re[1].startsWith("/echo/")) {
        String ec = re[1].replaceFirst("/echo/", "");
        sendCode(sock, "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + ec.length() + "\r\n\r\n" + ec);
      }
      if (re[1].equals("/user-agent")) {
        String[] ua = inp.get(2).split(" ");
        sendCode(sock, "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + ua[1].length() + "\r\n\r\n" + ua[1]);
      }
      if (re[1].startsWith("/files/")){
        String fi = re[1].replaceFirst("/files/", "");
        if(Objects.equals(re[0], "GET") && Files.exists(Path.of(dir + "/" + fi))) {
          String cc = Files.readString(Path.of(dir + "/" + fi));
          sendCode(sock, "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " + cc.length() + "\r\n\r\n" + cc);
        } else if (Objects.equals(re[0], "POST")) {
          System.out.println(inp);
          File file = new File(dir + "/" + fi);
          if(file.createNewFile()) {
            String[] lengt = inp.get(3).split(" ");
            char[] bc = new char[Integer.parseInt(lengt[1])];
            bif.read(bc, 0, Integer.parseInt(lengt[1]));
            String bd = new String(bc);
            Files.write(Path.of(dir + "/" + fi), bd.getBytes());
            sendCode(sock, "HTTP/1.1 201 OK\r\n\r\n");
          }
        }
        {
          sendCode(sock, "HTTP/1.1 404 Not Found\r\n\r\n");
        }
      } else {
        sendCode(sock, "HTTP/1.1 404 Not Found\r\n\r\n");
      }

      System.out.println("accepted new connection");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void sendCode(Socket sock, String code) throws IOException {
    byte[] msg = code.getBytes();

    OutputStream out = sock.getOutputStream();
    out.write(msg);
    out.flush();
    out.close();
  }
}