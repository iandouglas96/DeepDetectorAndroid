package com.iandm.astrobee.deepdetector;

import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import gov.nasa.arc.astrobee.android.gs.MessageType;
import gov.nasa.arc.astrobee.android.gs.StartGuestScienceService;

public class StartDeepDetector extends StartGuestScienceService {
    @Override
    public void onGuestScienceCustomCmd(String command) {
        sendReceivedCustomCommand("info");

        try {
            JSONObject obj = new JSONObject(command);
            //Command type
            String commandStr = obj.getString("name");

            //Command args
            String commandVal = new String("");
            if (obj.has("value")) {
                commandVal = obj.getString("value");
            }

            JSONObject jResponse = new JSONObject();
            Intent intent = new Intent();

            switch (commandStr) {
                case "turnOnDetector":
                    intent.setAction(DeepDetector.TURN_ON_DETECTOR);
                    sendBroadcast(intent);
                    jResponse.put("Summary", "Command to turn on detector sent.");
                    break;
                case "turnOffDetector":
                    intent.setAction(DeepDetector.TURN_OFF_DETECTOR);
                    sendBroadcast(intent);
                    jResponse.put("Summary", "Command to turn off detector sent.");
                    break;
                default:
                    jResponse.put("Summary", "ERROR: Command not found.");
                    break;
            }
            sendData(MessageType.JSON, "data", jResponse.toString());
        } catch (JSONException e) {
            sendData(MessageType.JSON, "data", "{\"Summary\": \"Error parsing JSON:(\"}");
            e.printStackTrace();
        }
    }

    @Override
    public void onGuestScienceStart() {
        Log.i("deep_det", "Started Module");
        sendStarted("info");
    }

    @Override
    public void onGuestScienceStop() {
        Log.i("deep_det", "Stopped Module");
        sendStopped("info");
        terminate();
    }
}
