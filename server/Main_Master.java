

import java.net.*;
import java.util.ArrayList;
import java.util.regex.Pattern;



import java.io.*;

class Master{
    final static int NUM_ARGS = 5;
    //docker run --net=pub_net -d --ip=10.0.0.201 --hostname="test"  --name="remote-desktop" --rm --name="remote-desktop" scottyhardy/docker-remote-desktop
    //docker container stop $(docker container ls -q --filter name=remote*)
    public static void main(String[] args) {
        // check if length of args array is 
        // greater than 0 
        try{
            if (args.length == NUM_ARGS || (args.length >= 2 && args[0].equals("destroy"))) 
            { 
                if(args.length >= 4 && args[0].equals("destroy")){
                    File f;
                    try {
                        f = new File(args[1]);
                    }catch(Exception e){
                        System.out.println("invalid path");
                        throw new Error();
                    }

                    String token = args[2];
                    int port;
                    try {
                        port = Integer.parseInt(args[3]);
                        if(port == 0){
                            port = 3066;
                            System.out.println("using default port of 3066");
                        }
                        else if(port <= 1023) {
                            System.out.println("you are not allowed to use a port that is between 0-1023");
                            throw new Error();
                        }else if (port > 65535) {
                            System.out.println("you cannot use a port that doesn't exist!");
                            throw new Error();
                        }else{
                            System.out.println("using port " + port);
                        }
                    }catch(Exception e){
                        System.out.println("the port needs to be in numerical form");
                        throw new Error();
                    }
                    ArrayList<String> hosts;
                    try{
                        hosts = readFromFile(f);
                    }catch(Exception e){
                        e.printStackTrace();
                        throw new Error();
                    }
                    
                    
                    for(String s : hosts){
                        System.out.println("the ip is " + s );
                        Destroy d = new Destroy(s, token, port);
                        d.destroy();
                    }

                }else{
                    System.out.println("The command line arguments are:"); 
                    
                    //get path
                    File f;
                    int containers;
                    int port;
                    try {
                        f = new File(args[0]);
                    }catch(Exception e){
                        System.out.println("invalid path");
                        throw new Error();
                    }
                    
                    //starting ip
                    String startIp = args[1];
                    System.out.println("starting ip is " + startIp);
                    
                    try {
                        containers =  Integer.parseInt(args[2]);
                    }catch(Exception e){
                        System.out.println("the number of containers needs to be in numerical form");
                        throw new Error();
                    }
                    
                    
                    
                    //secret token
                    String secret = args[3];

                    System.out.println("token is " + secret);
                    
                    //port for server
                    try {
                        port = Integer.parseInt(args[4]);
                        if(port == 0){
                            port = 3066;
                            System.out.println("using default port of 3066");
                        }
                        else if(port <= 1023) {
                            System.out.println("you are not allowed to use a port that is between 0-1023");
                            throw new Error();
                        }else if (port > 65535) {
                            System.out.println("you cannot use a port that doesn't exist!");
                            throw new Error();
                        }else{
                            System.out.println("using port " + port);
                        }
                    }catch(Exception e){
                        System.out.println("the port needs to be in numerical form");
                        throw new Error();
                    }
                    ArrayList<String> hosts;
                    try{
                        hosts = readFromFile(f);
                    }catch(Exception e){
                        System.out.println("something is wrong with the file");
                        throw new Error();
                    }
                    int rem;
                    int count;
                    rem = containers % hosts.size();
                    count = (containers - rem)/hosts.size();
                    for(String s : hosts){
                        int temp = count;
                        if(rem > 0){
                            temp++;
                            rem--;
                        }                    
                        

                        Connect c = new Connect(s, port, secret, startIp, temp);
                        c.start();
                        startIp = Utils.nextIp(startIp, temp);
                    }
                }
            } 
            else
            {
                System.out.println("format should be: [server list file path] [starting ip] [number of containers] [secret token] [port]"); 
                System.out.println("or: destroy [server list file path] [secret token] [port]"); 
                throw new Error();
            }
        }finally{
            //dop nothing
        }

    }

    private static ArrayList<String> readFromFile(File f) throws IOException {
        ArrayList<String> contents;
        try(BufferedReader r = new BufferedReader(new FileReader(f))){
            contents = new ArrayList<>();

            
            String line = r.readLine();
            while(line != null){
                contents.add(line);

                line = r.readLine();
            }
        }catch(IOException e){
            throw e;
        }

        return contents;
    }

}

class Connect extends Thread{
    private String ip, token, startingIp;
    private int port, count;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    public Connect(String ip, int port, String token, String startingIp, int count){
        this.ip = ip;
        this.port = port;
        this.token = token;
        this.startingIp = startingIp;
        this.count = count;
        
    }


    @Override
    public void run(){
        System.out.println("attempting to make first connection");
        try(Socket s = new Socket(ip, port)){
            if(s.isConnected()){
                System.out.println("connection accepted");
            }
            System.out.println("getting output stream");
            out = new ObjectOutputStream(s.getOutputStream());
            System.out.println("got output stream");
            
            System.out.println("sending first message");
            out.writeObject(new Message(Operation.LOGIN, new String[]{token}));
            System.out.println("message sent");
            in = new ObjectInputStream(s.getInputStream());
            Message m = (Message) in.readObject();
            if(m.o == Operation.FAIL){
                throw new Error();
            }
            String temp = startingIp;
            
            for(int i = 0; i < count; i++){
                out.writeObject(new Message(Operation.CREATE, new String[]{temp}));
                m = (Message) in.readObject();
                System.out.println(m.args[0]);
                if(m.o == Operation.FAIL){
                    throw new Error();
                }
                System.out.println("temp is " + temp);
                
                temp = Utils.nextIp(temp);
                System.out.println("next ip is " + temp);
            }
            out.writeObject(new Message(Operation.DISCONNECT, new String[]{""}));


        }catch(Exception e){
            e.printStackTrace();
        }
        
        
    }
}

class Destroy{
    private String ip;
    private String token;
    private int port;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    public Destroy(String ip, String token, int port){
        this.ip = ip;
        this.token = token;
        this.port = port;
    }

    public void destroy(){
        try(Socket s = new Socket(ip, port)){
            if(s.isConnected()){
                System.out.println("connection accepted");
            }
            System.out.println("getting output stream");
            out = new ObjectOutputStream(s.getOutputStream());
            System.out.println("got output stream");
            
            System.out.println("sending first message");
            out.writeObject(new Message(Operation.LOGIN, new String[]{token}));
            System.out.println("message sent");
            in = new ObjectInputStream(s.getInputStream());
            Message m = (Message) in.readObject();
            if(m.o == Operation.FAIL){
                throw new Error();
            }

            System.out.println("sending destroy message");
            
            out.writeObject(new Message(Operation.DESTROY, new String[]{"none"}));
            System.out.println("message sent");

            m = (Message) in.readObject();
            if(m.o == Operation.FAIL){
                throw new Error();
            }
            out.writeObject(new Message(Operation.DISCONNECT, new String[]{""}));
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}

class Utils{

    private Utils(){
        //this is gonna stay empty
    }

    public static String nextIp(String ip){
        System.out.println("ip is " + ip);
        String[] bytes = ip.split(Pattern.quote("."));
        
        int b =  Integer.parseInt(bytes[3]);
        
        b++;
        bytes[3] = Integer.toString(b);
        if(b >= 256){
            bytes[3] = Integer.toString(0);
            b =  Integer.parseInt(bytes[2]);
            b++;
            bytes[2] = Integer.toString(b);
            if(b >= 256){
                bytes[2] = Integer.toString(0);
                b =   Integer.parseInt(bytes[1]);
                b++;
                bytes[1] = Integer.toString(b);
                if(b >= 256){
                    bytes[1] = Integer.toString(0);
                    b =  Integer.parseInt(bytes[1]);
                    b++;
                    bytes[0] = Integer.toString(b);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(bytes[0]);
        sb.append(".");
        sb.append(bytes[1]);
        sb.append(".");
        sb.append(bytes[2]);
        sb.append(".");
        sb.append(bytes[3]);
        return sb.toString();
    }

    public static String nextIp(String ip, int count){
        String out = ip;
        for(int i = 0; i < count; i++){
            out = nextIp(out);
        }
        return out;
    }
}

class Message implements Serializable{

    /**
     *
     */
    private static final long serialVersionUID = 123645;
    public Operation o;
    public String[] args;
    public Message(Operation o, String[] args){
        this.o = o;
        this.args = args;
    }

}

enum Operation{
    LOGIN,
    CREATE,
    SUCCESS,
    FAIL,
    DISCONNECT,
    DESTROY,
    STATUS;
}