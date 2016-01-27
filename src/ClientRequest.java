import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by AvivPC on 16-Jan-16.
 */
public class ClientRequest {
    final static String getRequest = "GET ";
    final static String headRequest = "HEAD ";
    final static int port = 80;
    final static String CRLF = "\r\n";
    private String headers;
    private String body;
    private String host;
    private String location;
    private String requestType;

    final static String newLine = System.lineSeparator();
    public HashMap<String, String> responseHeaderFields;
    private String unparsedResponse;
    private String responseStatus;
    private String responseStatusCode;
    private String responseHttpVersion;
    private String[] parsedRequest;
    private static final Pattern urlPattern = Pattern.compile(".*?(http:\\/\\/|https:\\/\\/)?(www.)?(.*?)(\\/.*)$");

    public ClientRequest(String url, String requestType) throws IOException {
        parseURL(url);
        //host = "www.google.com";
        //location = "\\";
        this.requestType = requestType;
        try {
            Socket socket = new Socket(host, port);
            DataOutputStream socketOutputStream = new DataOutputStream(socket.getOutputStream());
            //BufferedOutputStream socketOutputStream = new BufferedOutputStream(socket.getOutputStream());
            //PrintStream PS = new PrintStream(socket.getOutputStream());
            //PS.println("HEAD / HTTP/1.0");
            if (requestType.equals(headRequest)) {
                socketOutputStream.writeBytes(headRequest + location + " HTTP/1.0" + CRLF);
                socketOutputStream.writeBytes("Host: " + host + CRLF + CRLF);
                socketOutputStream.flush();
            } else if (requestType.equals(getRequest)) {
                socketOutputStream.writeBytes("GET " + location + " HTTP/1.0" + CRLF);
                socketOutputStream.writeBytes("Host: " + host + CRLF + CRLF);
                socketOutputStream.flush();
            }

            InputStreamReader IR = new InputStreamReader(socket.getInputStream());
            BufferedReader BR = new BufferedReader(IR);
            String line = BR.readLine();
            StringBuilder response = new StringBuilder();
            while (line != null) {
                response.append(line + System.lineSeparator());
                line = BR.readLine();
            }
            if (requestType.equals(headRequest)) {
                headers = response.toString();
                body = null;
            } else if (requestType.equals(getRequest)) {
                String[] splitResponse = response.toString().split(CRLF + CRLF);
                headers = splitResponse[0];
                body = splitResponse[1];
            }
            parseResponse(headers);
        }catch (Exception e){
            System.out.println("Failed to connect to + " + url);
        }
        //System.out.print(response.toString());
        //System.out.println("Status Code is: " + getResponseStatusCode());
        //System.out.println("Status is: " + getResponseStatus());
        //System.out.println("Body is: " + body);




    }
    private void parseResponse(String unparsedResponse){

        this.unparsedResponse = unparsedResponse;

        // Divide the string to lines
        String[] requestLines = unparsedResponse.split(newLine);
        if (requestLines.length < 1) {
            return;
        }

        String[] header = requestLines[0].split(" ");
        if (header.length < 3) {
            return;
        }

        responseHttpVersion = header[0];
        responseStatusCode = header[1];
        responseStatus = header[2];
        parsedRequest = requestLines;

        responseHeaderFields = getHeaders(parsedRequest);

    }


    private void parseURL(String url){
        //Group(1) is http:// or https://
        //Group(2) is www.
        //Group(3) is host
        //Group(4) is location
        try {
            Matcher matcher = urlPattern.matcher(url);
            if (matcher.find()) {

                this.host = matcher.group(3);

                if (matcher.group(4) == "null") {
                    this.location = "//";
                } else {
                    this.location = matcher.group(4);
                }

                //System.out.println("Host is: " + host + CRLF + "Location is: " + location);
            }else{
                if(!url.endsWith("/")){
                    parseURL(url + "/");
                }
            }
        }catch(Exception e){
            System.out.println("Failed to parse the Url: " + url);
        }

    }

    private HashMap<String, String> getHeaders(String[] headersInString){
        HashMap<String, String> result =  new HashMap<>();
        //String[] splitHeaders = headersInString.split(CRLF);
        for (String line : headersInString)
        {
            String[] splitLine = line.split(": ");
            if(splitLine.length == 2){
                result.put(splitLine[0], splitLine[1]);
                //System.out.println("Key: " + splitLine[0] + ", value: " + splitLine[1]);
            }
        }
        return result;
    }

    public String getResponseStatusCode(){
        return responseStatusCode;
    }

    public String getResponseStatus(){
        return responseStatus;
    }

    public String getResponseHttpVersion(){
        return responseHttpVersion;
    }

    public String getBody() { return body; }
}

