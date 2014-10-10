package com;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

class UDPServer
{
    public static void main(String args[]) throws Exception
      {
         @SuppressWarnings("resource")
		DatagramSocket  serverSocket = new DatagramSocket(5678);
         
          final  byte[] receiveData  = new byte[512];
            while(true)
               {
                  DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                  serverSocket.receive(receivePacket);
                  //String sentence = new String( receivePacket.getData());
                  if(handleClientRequest(receivePacket,serverSocket)) 
                		  System.out.println("Action Success");
                  else System.out.println("Error");
                  serverSocket.close();
                  System.out.println("tx completed ....");
                  serverSocket= new DatagramSocket(5678);
                  
                  
               }
    }

   /*

          opcode  operation
            1     Read request (RRQ)
            2     Write request (WRQ)
            3     Data (DATA)
            4     Acknowledgment (ACK)
            5     Error (ERROR) 
    * */
    private static Boolean handleClientRequest(DatagramPacket receivePacket, DatagramSocket serverSocket) throws IOException {
	  
	     byte[] dataPacket =receivePacket.getData();
	     byte[] opcode = {dataPacket[0] ,dataPacket[1]}; 
	     ByteBuffer buff = ByteBuffer.wrap(opcode);
	     short Opcode=buff.getShort();
	 
    	 Packet  packet = new Packet (Opcode, receivePacket);//packet object
	     if(Opcode==1)return readRequest(packet,serverSocket);
	     if(Opcode==2)return writeRequest(packet,serverSocket);
	     return false; 
	     }

     private static boolean writeRequest(Packet packet, DatagramSocket serverSocket) throws IOException {
	     String HOME="./";
	     File file;
	     if((file=findFile(packet.FileName, new File(HOME)))!=null){
		     packet.sendErrorMsg((short)6, packet.TFTP_ERROR_6, serverSocket, packet);
	     }
	     else{
		     byte[] receiveData = new byte[512];
		     int packet_counter=1;
		     file=new File(HOME+packet.FileName);
		     packet.sendAck(packet, (short)0, serverSocket);
	         DatagramPacket recievePacket = new DatagramPacket(receiveData, receiveData.length);
	         serverSocket.receive(recievePacket);
	         
	         while(recievePacket.getData().length>=512){
	        	
			    Packet pkt= new Packet((byte )3, recievePacket);
			    if(writeFile(packet.FileName,pkt)){
		    	    packet.sendErrorMsg((byte)(3), packet.TFTP_ERROR_3, serverSocket, packet);
		    	    return false;
		        }
			    
			 if(packet_counter!=pkt.block)return false;//to check if I am receiving unwanted block number   
			 packet.sendAck(packet, pkt.block, serverSocket);	
			 recievePacket = new DatagramPacket(receiveData, receiveData.length);
	         serverSocket.receive(recievePacket);
	         packet_counter++;
	         }
	    }
	    return true;
    }



    private static boolean writeFile(String fileName, Packet pkt) throws IOException {
	    FileWriter fw = new FileWriter(fileName,true); 
	    fw.write(pkt.Data);

	    fw.close();
        return false;
    }

    private static boolean readRequest(Packet packet, DatagramSocket serverSocket) throws IOException {
	    String HOME="./";
        long size = findFileSize(packet.FileName, new File(HOME));
        File file;
	    if((file=findFile(packet.FileName, new File(HOME)))!=null){
		    packet.sendAck(packet,(short)0,serverSocket);
		    if(sendFile(packet,serverSocket,file)) return true;
	    }
	    else{
		    //sendError(packet);
		    packet.sendErrorMsg((short)1,packet.TFTP_ERROR_1,serverSocket,packet);
		    System.out.println("File Not Found");
	        }
	    return true;
    }

    private static boolean sendFile(Packet packet, DatagramSocket serverSocket,
	    
    	File file) throws IOException {
	    int offset = 0;
        int length = 511;
        int blockNumber=1;
	    byte[] readBytes = new byte[512];
	    //DatagramPacket packetReceived = null;
	    FileInputStream is = new FileInputStream(file);
	    int fileBytes = is.read(readBytes, offset, length);
		packet.sendData(packet, serverSocket, blockNumber,readBytes);
		
	    
	    while(fileBytes!=-1){
	    	offset+=508;
	    	blockNumber++;
	    	if(is.available()>=508)
		    fileBytes = is.read(readBytes);
	    	else {
	    		//fileBytes = is.read(readBytes, offset,is.available());
	    		return true;
	    	}
	    	is.mark(508);
	    	
		    System.out.println(" block no == "+blockNumber);    
		    packet.sendData (packet, serverSocket, blockNumber,readBytes);
		    
		    
		    
	    }
	    return true;
	
	}


    private static long findFileSize(String fileName, File file) {
	    if(findFile(fileName, file)!=null)
		    return findFile(fileName, file).length();
	    else return -1;
    }

    private static  File findFile(String name, File file) {
	
        File[] list = file.listFiles();
	
        if(list!=null)
        for (File fil : list){ 
            if (fil.isDirectory()){
                 findFile(name,fil);
            }
            else if (name.equalsIgnoreCase(fil.getName()))
            {
        	    return fil;
            }
        }
        return null;
    }

}





class Packet {
    short Opcode;       //2 byte
	String FileName;
	String Mode;
	short block;       //2 byte
	String Data;
	short ErrorCode;   //2 byte 
	String ErrMsg;
	InetAddress IPAddress;
	int clientPort; 
	
    public final byte[]  sendData     = new byte[512];
    public static final String TFTP_ERROR_0 = "Not defined, see error message";
    public static final String TFTP_ERROR_1 = "File not found";
    public static final String TFTP_ERROR_2 = "Access violation";
    public static final String TFTP_ERROR_3 = "Disk full or allocation exceeded";
    public static final String TFTP_ERROR_4 = "Illegal TFTP operation";
    public static final String TFTP_ERROR_5 = "Unknown transfer ID";
    public static final String TFTP_ERROR_6 = "File already exists";
    public static final String TFTP_ERROR_7 = "No such user";

    public Packet(short opcode, DatagramPacket receivePacket)
		  throws UnsupportedEncodingException {
		  byte[] data=receivePacket.getData();
		  String stringRcvd = new String(data, "UTF-8");
		  IPAddress=receivePacket.getAddress();
		  clientPort=receivePacket.getPort();
		  int index;
		  short var;
		  ByteBuffer buff;
		  this.Opcode=opcode;
		  
		  switch (Opcode){

    	      /*
    	                   Read/Write Request
    	                  --------------------
    	               
    	       2 bytes     string    1 byte     string   1 byte
              ------------------------------------------------
             | Opcode |  Filename  |   0  |    Mode    |   0  |
              ------------------------------------------------
    	 
    	     */
            case 1:
            	
            	index=2;
            	
            	while(stringRcvd.charAt(index)+0!=0){
            		index++;
            	}
                 	
            	this.FileName=(String)(stringRcvd.substring(2,index));
            	index++;
            	while(data[index]!=0){
            		index++;
            	}
            	this.Mode=(String)(stringRcvd.substring((3+this.FileName.length()),index));
            	break;
            	
            	
            	
  	        case 2:      //Write request (WRQ) Packet
  	        	  
  	        	
  	        	
            	index=2;
            	while(stringRcvd.charAt(index)!=0){
            		index++;
            		if(index>=stringRcvd.length())break;
            	}
                 	
            	this.FileName=(String)(stringRcvd.substring(2,index));
            	index++;
            	while(data[index]!=0){
            		index++;
            	}
            	this.Mode=(String)(stringRcvd.substring((3+this.FileName.length()),index));
            	break;
            	
            	/*
            	 *           Data Packet
            	            -------------

                   2 bytes     2 bytes      n bytes
                   ----------------------------------
                  | Opcode |   Block #  |   Data     |
                   ----------------------------------
            	 */
            	
            case 3:     //Data Packet
                    	 
           	      byte[] blk= {data[2] ,data[3]};
                  buff = ByteBuffer.wrap(blk);
                  this.block=buff.getShort();
           	      String d="";
           	      for(int i=4;i<data.length;i++){
           	    	  d=d+(char)(data[i] & 0xff);
           	    	//System.out.println(i +"     "+ d);
           	      }
           	      this.Data=d.toString();
           	      break;
                           	
            	/*            Ack Packet
            	 *  
                         2 bytes     2 bytes
                         ---------------------
                        | Opcode |   Block #  |
                         ---------------------
            	 * */
            case 4:     //Acknowledgment (ACK) Packet
            	  byte[] b= {data[2] ,data[3]};
            	  buff=ByteBuffer.wrap(b);
            	  this.block=buff.getShort();
            	
            	
            	/*             Error Packet
            	 *              
                      2 bytes     2 bytes      string    1 byte
                      -----------------------------------------
                     | Opcode |  ErrorCode |   ErrMsg   |   0  |
                      -----------------------------------------
            	 * */
            	  
            case 5:     //Error (ERROR)
            	byte[] errCode= {data[2] ,data[3]};
                 buff = ByteBuffer.wrap(errCode);
                 this.ErrorCode=buff.getShort();
                 this.ErrMsg= (String)(data.toString().substring(4,data.length-1));  
            	 
	    }
		  
		
	}
//file not found error	
   public void sendErrorMsg(short errCode, String errMsg, DatagramSocket serverSocket, Packet packet) throws IOException {

	   byte[] sendBytes=new  byte[512];
	   
	    ByteBuffer buffer = ByteBuffer.allocate(2);
	    buffer.putShort(errCode);
	    buffer.flip();
	    sendBytes[0]=0;
	    sendBytes[1]=5;// for error 
	    sendBytes[2]=(buffer.array())[0];
	    sendBytes[3]= (buffer.array())[1];
	    byte[] bytes= errMsg.getBytes();
	    for(int i=0;i<bytes.length;i++)
	    	sendBytes[i+4]=bytes[i];
	    sendBytes[bytes.length+4]=(byte)0;
	    DatagramPacket sendPacket =new DatagramPacket(sendBytes, bytes.length+5, packet.IPAddress, packet.clientPort);
	    serverSocket.send(sendPacket);
	    
	   
	}

   public void sendAck(Packet packet, short l, DatagramSocket serverSocket) throws IOException {
	    
	    byte[] ack= new byte[4];
	    short bytes=4;
	    ByteBuffer buffer = ByteBuffer.allocate(2);
	    buffer.putShort(bytes);
	    buffer.flip();
	    ack[0]=(buffer.array())[0];
	    ack[1]=(buffer.array())[1];
	    buffer.putShort(l);
	    buffer.flip();
	    ack[2]=(buffer.array())[0];
	    ack[3]=(buffer.array())[1];
	    DatagramPacket sendPacket =new DatagramPacket(ack, ack.length, packet.IPAddress, packet.clientPort);
	    System.out.println("\nSENT ACK with block no "+ l);
	    serverSocket.send(sendPacket);
    }
   
   /*
    * 
                   2 bytes     2 bytes      n bytes
                   ----------------------------------
                  | Opcode |   Block #  |   Data     |
                   ----------------------------------
    * */
   
   public boolean sendData(Packet packet,DatagramSocket serverSocket, int block,byte[] data  ) throws IOException{
	   
	   byte[] sendBytes=new  byte[512];
	   byte byteBlock = (byte)block;
	   byte[] buffer=new byte[2];
	   ByteBuffer buffer1 = ByteBuffer.allocate(2);
	   
	   buffer1.putShort((short) block);
	   buffer1.flip();
	   
	   int dataOffset = 0;
	   
	   sendBytes[0]=0;
	   sendBytes[1]=3;
	   sendBytes[2]=buffer1.array()[0];
	   sendBytes[3] =buffer1.array()[1];
	   for(;dataOffset<508;dataOffset++){
		  
		   sendBytes[(dataOffset)+4] = data[dataOffset];
	      
	       if( data[dataOffset]==0)break;
	   
	   }
	   
	   DatagramPacket sendPacket =new DatagramPacket(sendBytes, data.length, packet.IPAddress, packet.clientPort);
	   serverSocket.send(sendPacket);   
	   return true;
   }
  

	
}

