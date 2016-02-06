import java.io.*;
import java.net.InetSocketAddress;
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
    public String host;
    private String location;
    private String requestType;

    final static String newLine = System.lineSeparator();
    public HashMap<String, String> responseHeaderFields;
    private String unparsedResponse;
    private String responseStatus;
    private String responseStatusCode;
    private String responseHttpVersion;
    private String[] parsedRequest;
    BufferedReader inputBuffer;
	//private static final Pattern urlPattern = Pattern.compile("((^[Hh][Tt][Tt][Pp][Ss]?):\\/\\/)?((www.)?(.*))");
    private static final Pattern urlPattern = Pattern.compile(".*?(http:\\/\\/|https:\\/\\/)?(www.)?(.*?)(\\/.*)$");

    public static void main(String[] args) throws IOException {
        String url = "http://www.ynet.co.il//articles//0,7340,L-4759862,00.html";
        ClientRequest testing = new ClientRequest(url, getRequest);

        System.out.println("-------------------------------------------------------------------------");;
        testing.getLinksFromHtml(testing.getBody(), "Ynet4");
        //System.out.println("!!!!!!" + testing.getBody());
        File bodyOutput = new File("C:\\Users\\AvivPC\\Desktop\\ForCrawler\\body.txt");
        FileWriter fw = new FileWriter(bodyOutput);
        fw.write(testing.getBody());
        fw.close();

       //System.out.println(isLinkValid("/dy2.ynet.co.il/scripts/8765235/api_dynamic.js"));
    }

    public ClientRequest(String url, String requestType) throws IOException {
    	
        parseURL(url);
        this.requestType = requestType;
        Socket socket = new Socket();
        try {
        	System.out.println("Connecting socket to: " + host);
            socket.connect(new InetSocketAddress(host, port));
            System.out.println("Creating new OutputStream");
            DataOutputStream socketOutputStream = new DataOutputStream(socket.getOutputStream());
            //BufferedOutputStream socketOutputStream = new BufferedOutputStream(socket.getOutputStream());
            //PrintStream PS = new PrintStream(socket.getOutputStream());
            //PS.println("HEAD / HTTP/1.0");
            if (requestType.equals(headRequest)) {
                socketOutputStream.writeBytes(headRequest + location + " HTTP/1.1" + CRLF);
                socketOutputStream.writeBytes("Host: " + host + CRLF + CRLF);
                socketOutputStream.flush();
            } else if (requestType.equals(getRequest)) {
                socketOutputStream.writeBytes("GET " + location + " HTTP/1.1" + CRLF);
                socketOutputStream.writeBytes("Host: " + host + CRLF + CRLF);
                socketOutputStream.flush();
            }

            InputStreamReader IR = new InputStreamReader(socket.getInputStream());


            char[] buffer = new char[1024];
            int indexOfBuffer = 0;
            StringBuilder HeaderResponse = new StringBuilder();
            System.out.println("1");
            
            //Read Header
            indexOfBuffer = IR.read();
            while (indexOfBuffer > 0) {
            	HeaderResponse.append((char)indexOfBuffer);
            	if (HeaderResponse.toString().endsWith(CRLF + CRLF)) {
            		break;
            	}
            	//System.out.println("Index of buffer: " + indexOfBuffer);
            	indexOfBuffer = IR.read();
            	//System.out.println("Index of buffer2: " + indexOfBuffer);
            }
            System.out.println("Finished reading header");
            System.out.println(HeaderResponse.toString());
            parseResponse(HeaderResponse.toString());



            // Read Body
            StringBuilder BodyResponse = new StringBuilder();
            int contentLength = 0;
            if (responseHeaderFields.containsKey("Content-Length")) {
            	contentLength = Integer.parseInt(responseHeaderFields.get("Content-Length"));
            	System.out.println("ContentLength is: " + contentLength);
            	body = "";
                if (contentLength > 0) {
                    inputBuffer = new BufferedReader(IR);
                    String line = inputBuffer.readLine();

                    while(line != null){
                       BodyResponse.append(line);
                        line = inputBuffer.readLine();
                    }
                    body = BodyResponse.toString();

                	System.out.println("Finished reading body");
                    //System.out.println("*********************Body for " + url + " *************************");
                    //System.out.println(body);
                    //System.out.println("*********************End of Body for " + url + " ******************");
                }
            } else if(responseHeaderFields.containsKey("Transfer-Encoding")){
                //TODO: delete this
                //System.out.println("!!!!!!!!!!We have chunk data!");
                inputBuffer = new BufferedReader(IR);
                body = readChunkedData();

            }else{
                System.out.println("Error Reading body!");
                body = "";
            }

            /*if (requestType.equals(headRequest)) {
                headers = response.toString();
                body = null;
            } else if (requestType.equals(getRequest)) {
                String[] splitResponse = response.toString().split(CRLF + CRLF);
                System.out.println(response.toString());
                headers = splitResponse[0];
                if (splitResponse.length > 1)
                	body = splitResponse[1];
            }
            parseResponse(headers);*/

        }catch (Exception e){
            System.out.println("Failed to connect to " + url);
            e.printStackTrace();
        } finally {

        	if (socket != null)
        		socket.close();
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
    	System.out.println("Parse URL in Client request " + this.toString() + " got1: " + url);
        try {
            Matcher matcher = urlPattern.matcher(url);
            if (matcher.find()) {
            	
                if (matcher.group(2) == null) {
                	this.host = matcher.group(3);
                } else {
                	this.host = matcher.group(2) + matcher.group(3);	
                }
            	
                System.out.println("Parse URL in Client request host: " + host);

                if (matcher.group(4) == "null") {
                    this.location = "//";
                } else {
                    this.location = matcher.group(4);
                    
                }
                System.out.println("Parse URL in Client request location: " + location);
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
                System.out.println("Key: " + splitLine[0] + ", value: " + splitLine[1]);
            }
        }
        return result;
    }
    private String readChunkedData() throws IOException {
        StringBuilder SB = new StringBuilder();
        while(true) {
            String line = inputBuffer.readLine();
            if (line == null) {
                throw new IOException();
            }
            if (line == CRLF) {
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(line, 16);
            }catch(Exception e){
                index = -1;
            }
            if(index == 0){
                return SB.toString();
            }else if(index > 0){
                //System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
                //System.out.println("Number found: " + index);
                //System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
                if(SB.length() > 0) {
                    SB.deleteCharAt(SB.length() - 1);
                    SB.deleteCharAt(SB.length() - 1);
                }
                continue;
            }else{
                SB.append(line);
            }
            //System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");

        }
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

    //TODO: delete this method - it's for debbug only
    public Set<String> getLinksFromHtml(String HTMLPage, String name) throws IOException {

        //System.out.println("Analyzer is parsing: " + HTMLPage);
        Pattern linkPattern = Pattern.compile("href= *' *(.*?)'|href= *\" *(.*?)\"|src= *' *(.*?)'");
        Matcher pageMatcher = linkPattern.matcher(HTMLPage);
        Set<String> links = new HashSet<>();
        File linksExtacted = new File("C:\\Users\\AvivPC\\Desktop\\ForCrawler\\linksExtracted - " + name + ".txt");
        FileWriter fw = new FileWriter(linksExtacted);
        int index = 0;
        while(pageMatcher.find()){
            if(pageMatcher.group(1) != null) {
                links.add(pageMatcher.group(1));
                System.out.println("Link from analyzer: " + pageMatcher.group(1));
                if(isLinkValid(pageMatcher.group(1))) {
                    fw.write(pageMatcher.group(1) + System.lineSeparator());
                }else{
                    fw.write("Excluded: " + pageMatcher.group(1) + System.lineSeparator());
                }
                index++;
            }
            if(pageMatcher.group(2) != null){
                links.add(pageMatcher.group(2));
                System.out.println("Link from analyzer: " + pageMatcher.group(2));
                if(isLinkValid(pageMatcher.group(2))) {
                    fw.write(pageMatcher.group(2) + System.lineSeparator());
                }else{
                    fw.write("Excluded: " + pageMatcher.group(2) + System.lineSeparator());
                }
                index++;
            }
            if(pageMatcher.group(3) != null){
                links.add(pageMatcher.group(3));
                System.out.println("Link from analyzer: " + pageMatcher.group(3));
                if(isLinkValid(pageMatcher.group(3))) {
                    fw.write(pageMatcher.group(3) + System.lineSeparator());
                }else{
                    fw.write("Excluded: " + pageMatcher.group(3) + System.lineSeparator());
                }
                index++;
            }
            //System.out.println("Link from analyzer: " + pageMatcher.group(2));
        }
        fw.write(System.lineSeparator() + "Number of links extacted: " + index);
        fw.close();
        System.out.println("Number of links extacted: " + index);
        return links;
    }
    //TODO: delete this method - it's for debbug only
    public static boolean isLinkValid(String link){
        if(link.startsWith("//")){
            return false;
        }
        if(link.startsWith("android-app:")){
            return false;
        }
        if(link.startsWith("javascript:")){
            return false;
        }
        if(link.startsWith("\"+")){
            return false;
        }
        if(link.endsWith(".js")){
            return false;
        }
        if(link.startsWith("#")){
            return false;
        }
        return true;
    }
}

