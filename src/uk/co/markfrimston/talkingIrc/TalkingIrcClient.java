/* 
 * Based on SpeechBot by Paul James Mutton http://www.jibble.org
 * Licensed under the GNU General Public License (GPL) 
 */

package uk.co.markfrimston.talkingIrc;

import org.jibble.pircbot.PircBot;
import com.sun.speech.freetts.jsapi.*;
import java.io.*;
import javax.speech.EngineCreate;
import javax.speech.EngineList;
import javax.speech.EngineException;
import javax.speech.synthesis.Synthesizer;
import javax.speech.synthesis.SynthesizerModeDesc;
import java.util.Locale;
import java.util.Map;
import uk.co.markfrimston.utils.*;

public class TalkingIrcClient extends PircBot 
{
	protected static final CommandLineParameter PARAM_HOST = new CommandLineParameter("host","h","The hostname to connect to",1,null);
	protected static final CommandLineParameter PARAM_CHANNEL = new CommandLineParameter("channel","c","The channel to connect to",1,null);
	protected static final CommandLineParameter PARAM_NICK = new CommandLineParameter("nick","n","The nickname to appear as",1,null);
	
	protected static final long REM_LAST_SENDER = 10*1000;
	
    protected String name;
    protected Synthesizer synth;
    protected long lastMessageTime;
    protected String lastSender;
	
    public TalkingIrcClient() throws Exception
    {               
        this.synth = createSynth();
    }
    
    @Override
	protected void onConnect() 
    {
    	System.out.println("Connected");
    	speak("Connected");
	}

	@Override
	protected void onDisconnect() 
	{
		System.out.println("Disconnected");
		speak("Disconnected");
	}

	@Override
	protected void onAction(String sender, String login, String hostname,
			String target, String action) 
	{
		action = action.trim();
		printAction(sender, action);
		speakAction(sender, action);
	}

	public void onMessage(String channel, String sender, String login, String hostname, String message) 
    {
		message = message.trim();   
		printMessage(sender,message);
		speakMessage(sender,message);		
    }
	
	protected void printAction(String sender, String message)
	{
		System.out.println(sender+" "+message);
	}
	
	protected void speakAction(String sender, String message)
	{
		speak(sender+" "+message);
		
		lastMessageTime = 0;
		lastSender = null;
	}
	
	protected void printMessage(String sender, String message)
	{
		System.out.println("<"+sender+"> "+message);
	}
	
	protected void speakMessage(String sender, String message)
	{
		String speech = "";
        if(lastMessageTime==0 || lastSender==null || !sender.equals(lastSender)
        		|| System.currentTimeMillis()-lastMessageTime>REM_LAST_SENDER)
        {
        	speech += sender+": ";
        }
        speech += message;        
        speak(speech);
        
        lastMessageTime = System.currentTimeMillis();
        lastSender = sender;
	}
    
    protected Synthesizer createSynth() throws Exception
    {
    	FreeTTSEngineCentral central = new FreeTTSEngineCentral();
		EngineList engines = central.createEngineList(new SynthesizerModeDesc(
				null, 
                "general",
                Locale.US, 
                null,
                null));
		Synthesizer synth = null;
		if (engines.size() > 0) { 
            EngineCreate creator = (EngineCreate) engines.get(0); 
            synth = (Synthesizer) creator.createEngine(); 
        } 
        if (synth == null) {
            throw new Exception("Failed to create speech synth");
        }
    	synth.allocate();
    	synth.resume();
    	return synth;
    }

    protected void speak(String input) 
    {
    	synth.speakPlainText(input, null);
    	try{
    		synth.waitEngineState(Synthesizer.QUEUE_EMPTY);
    	}catch(InterruptedException e){}
    }

    protected boolean handleInput(String channel, String line)
    	throws IOException
    {
    	line = line.trim();
    	if(line.startsWith("/"))
    	{
    		String command, params;
    		int breakPos;
    		if((breakPos=line.indexOf(" "))!=-1)
    		{
    			command = line.substring(1,breakPos).trim();
    			params = line.substring(breakPos).trim();
    		}
    		else
    		{
    			command = line.substring(1).trim();
    			params = "";
    		}
    		
    		
    		if(command.equalsIgnoreCase("nick"))
    		{
    			this.changeNick(params);
    			this.setName(params);
    			System.out.println("Changed nick to "+params);
    		}
    		else if(command.equalsIgnoreCase("quit"))
    		{
    			if(params.length()>0){
    				this.quitServer(params);
    			}else{
    				this.quitServer();
    			}
    			System.out.println("Quit server");
    			return false;
    		}
    		else if(command.equalsIgnoreCase("me"))
    		{
    			this.sendAction(channel, params);
    			printAction(this.getName(),params);
    		}
    		else
    		{
    			System.out.println(command+" command not implemented");
    		}
    	}
    	else
    	{	    	
    		this.sendMessage(channel, line);
    		printMessage(this.getName(), line);
    	}
    	return true;
    }
    
    public void inputHandler(String channel) throws IOException
    {
    	BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    	String line = null;
    	    	
    	while((line=reader.readLine())!=null)
    	{
    		if(!handleInput(channel, line)){
    			break;
    		}
    	}    	
    }
    
    public void cleanUp() throws EngineException
    {
    	this.disconnect();
    	synth.deallocate();    	
    	this.dispose();
    }
    
    public static void main(String[] args)
    {
    	CommandLineParameter[] options = new CommandLineParameter[]{};
        CommandLineParameter[] params = new CommandLineParameter[]{ PARAM_HOST, PARAM_CHANNEL, PARAM_NICK };
        CommandLineParameter[] optParams = new CommandLineParameter[]{};
        Map<CommandLineParameter,String[]> paramVals = CommandLineUtils.parseParameters(options, params, optParams, args);
    	String hostname = paramVals.get(PARAM_HOST)[0];
    	String channel = paramVals.get(PARAM_CHANNEL)[0];
    	String nick = paramVals.get(PARAM_NICK)[0];
        try
        {
        	System.out.println("Connecting to "+hostname+" "+channel+" as "+nick);
        	TalkingIrcClient tic = new TalkingIrcClient();
        	tic.setName(nick);
        	tic.connect(hostname);
        	tic.joinChannel(channel);
        	tic.inputHandler(channel);
        	tic.cleanUp();
        	System.out.println("Closed");        	
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }
    }
}
