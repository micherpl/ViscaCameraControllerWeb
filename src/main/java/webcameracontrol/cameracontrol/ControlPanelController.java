package webcameracontrol.cameracontrol;

import jssc.SerialPort;
import jssc.SerialPortException;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import pl.edu.agh.kis.visca.ViscaResponseReader;
import webcameracontrol.cameracontrol.Model.Command;

import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.Queue;

@RestController
public class ControlPanelController {

    private static String portName = "COM15";

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView httpServicePostJSONDataExample(ModelMap model ) {
        return new ModelAndView("index.html");
    }
    @RequestMapping(value = "/command_json", method = RequestMethod.POST)
    public  @ResponseBody
    String saveCompany_JSON(@RequestBody Command commandModel )   {
        System.out.println("Orzymana komenda: "+commandModel.getCommand());

        SerialPort serialPort = new SerialPort(portName);
        byte [] command = new byte[0], answer;
        byte sourceAdr = 0, destinationAdr = 1;
        String[] comands;
        Queue<String> queueNumberParameters = new ArrayDeque<String>();
        LinkedList<String> instructions = new LinkedList<String>();

        comands = commandModel.getCommand().split(" ");
        for (String comand : comands) {
            if (comand.matches("^-?[0-9]{1,2}$")) {
                queueNumberParameters.offer(comand);
            } else {
                instructions.addFirst(comand);
            }
        }
        try {
            serialPort.openPort();
            serialPort.setParams(9600, 8, 1, 0);
            for (String instruction : instructions) {
                if (instruction.equals("poweron")) {
                    command = new byte[]{1, 4, 0, 2};
                    sourceAdr = 0;
                    destinationAdr = 1;
                } else if (instruction.equals("address")) {    //adresowanie kamer (daisy-chain) BROADCAST
                    command = new byte[]{0x30, 0x1};
                    sourceAdr = 0;
                    destinationAdr = 8;
                } else if (instruction.contains("move")) {  //ruch kamery
                    if (instruction.contains("up")) {     //w gore
                        command = new byte[]{1, 6, 1, 0, -1, 3, 1};
                        command[4] = Byte.parseByte(queueNumberParameters.poll());
                    } else if (instruction.contains("down")) { //w dol
                        command = new byte[]{1, 6, 1, 0, -1, 3, 2};
                        command[4] = Byte.parseByte(queueNumberParameters.poll());
                    } else if (instruction.contains("left")) { //w lewo
                        command = new byte[]{1, 6, 1, -1, 0, 1, 3};
                        command[3] = Byte.parseByte(queueNumberParameters.poll());
                    } else if (instruction.contains("right")) { //w prawo
                        command = new byte[]{1, 6, 1, -1, 0, 2, 3};
                        command[3] = Byte.parseByte(queueNumberParameters.poll());
                    } else if (instruction.contains("upleft")) { //w lewa gore
                        command = new byte[]{1, 6, 1, -1, -1, 1, 1};
                        command[3] = Byte.parseByte(queueNumberParameters.poll());
                        command[4] = Byte.parseByte(queueNumberParameters.poll());
                    } else if (instruction.contains("upright")) { //w prawa gore
                        command = new byte[]{1, 6, 1, -1, -1, 2, 1};
                        command[3] = Byte.parseByte(queueNumberParameters.poll());
                        command[4] = Byte.parseByte(queueNumberParameters.poll());
                    } else if (instruction.contains("downleft")) { //w lewy dol
                        command = new byte[]{1, 6, 1, -1, -1, 1, 2};
                        command[3] = Byte.parseByte(queueNumberParameters.poll());
                        command[4] = Byte.parseByte(queueNumberParameters.poll());
                    } else if (instruction.contains("downright")) { //w prawy dol
                        command = new byte[]{1, 6, 1, -1, -1, 2, 2};
                        command[3] = Byte.parseByte(queueNumberParameters.poll());
                        command[4] = Byte.parseByte(queueNumberParameters.poll());
                    } else {
                        System.err.println("Unrecognized command'");
                        continue;
                    }
                } else if (instruction.contains("zoom")) { //zmiana zoomu
                    if (instruction.contains("tele")) { //przybliz
                        command = new byte[]{1, 4, 7, 2};
                    } else if (instruction.contains("wide")) { //oddal
                        command = new byte[]{1, 4, 7, 3};
                    } else if (instruction.contains("stop")) { //zoom stop
                        command = new byte[]{1, 4, 7, 0};
                    } else {
                        System.err.println("Unrecognized command in \"zoom\"!");
                        continue;
                    }
                } else if (instruction.equals("wait")) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else if (instruction.equals("stop")) {  //zatrzymaj ruch
                    command = new byte[]{1, 6, 1, 0, 0, 3, 3};
                } else if (instruction.equals("home")) { //ustawienie poczatkowe
                    command = new byte[]{1, 6, 4};
                } else if (instruction.equals("reset")) { //reset
                    command = new byte[]{1, 6, 5};
                } else {
                    System.err.println("Unrecognized command '" + instruction);
                    continue;
                }
                command = getCommandData(sourceAdr, destinationAdr, command);
                System.out.println("SENT: " + byteArrayToString(command));
                serialPort.writeBytes(command);

                try {
                    answer = ViscaResponseReader.readResponse(serialPort);
                    System.out.println("RCVD: " + byteArrayToString(answer));
                    interpretResponse(byteArrayToString(answer));
                } catch (ViscaResponseReader.TimeoutException e) {
                    e.printStackTrace();
                }

            }
        } catch (SerialPortException e) {
            e.printStackTrace();
        }

        try {
            serialPort.closePort();
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
        return "JSON: The company name: ";
    }

    private static byte[] getCommandData(byte sourceAdr, byte destinationAdr, byte[] commandData) {
        int cmdLen = commandData.length + 1 + 1;
        byte[] cmdData = new byte[cmdLen];
        byte head = (byte)(128 | (sourceAdr & 7) << 4 | destinationAdr & 15);
        byte tail = -1;
        System.arraycopy(commandData, 0, cmdData, 1, commandData.length);
        cmdData[0] = head;
        cmdData[cmdData.length - 1] = tail;
        return cmdData;
    }

    private static String byteArrayToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", Byte.valueOf(b)));
        }
        return sb.toString();
    }

    private static void interpretResponse(String answer){
        String[] responseCodes = answer.split(" ");
        if(responseCodes[1].matches("^4.*")){
            System.out.println(" Command is accepted (ACK) ");
        } else if (responseCodes[1].matches("^5.*")) {
            System.out.println("  Command completion (the command has been executed) ");
        } else if (responseCodes[1].equals("50")) {
            System.out.println(" Information return ");
        } else if ((responseCodes[1].equals("60"))) {
            if(responseCodes[2].equals("01")) {
                System.out.println("Message length error (>14 bytes)");
            } else if(responseCodes[2].equals("02")) {
                System.out.println("Syntax error");
            } else if(responseCodes[2].equals("03")) {
                System.out.println("Command buffer full");
            } else if(responseCodes[2].equals("04")) {
                System.out.println("Command cancelled");
            } if(responseCodes[2].equals("05")) {
                System.out.println("No sockets");
            } if(responseCodes[2].equals("41")) {
                System.out.println("Command not executable");
            } else {
                System.out.println("Unknown response code");
            }
        }
        else {
            System.out.println("Unknown response code");
        }
    }
}
