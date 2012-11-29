/*
 * Created on Mar 9, 2010
 * Created by Paul Gardner
 * 
 * Copyright 2010 Vuze, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.frostwire.gui.mplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.limewire.util.FilenameUtils;
import org.limewire.util.OSUtils;

import com.frostwire.mplayer.Language;
import com.frostwire.mplayer.LanguageSource;
import com.frostwire.mplayer.PlayerPreferences;
import com.limegroup.gnutella.gui.MPlayerMediator;


public class 
MPlayerInstance 
{
	private static final boolean LOG	= false;
			
	private static File BINARY_PATH;

	public static void
	initialise(
		File		binary_path )
	{
		BINARY_PATH	= binary_path;
				
		killProcesses( false );
	}
	
	//private PlayerPreferences preferences;
	
	public MPlayerInstance(PlayerPreferences preferences) {
		//this.preferences = preferences;
	}
	
	public static void setBinaryPath(String path) {
		
	}

	private volatile Process	mPlayerProcess;
	
	private boolean	starting;
	private boolean	started;
	
	private boolean	stop_pending;
	private boolean	stopped;
	
	private AESemaphore	stop_sem = new AESemaphore( "EMP:S" );
	
	private boolean	paused;
	
	private List<String> 	commands 	= new LinkedList<String>();
	private AESemaphore		command_sem = new AESemaphore( "EMP:C" );
	
	private boolean 		isSeeking;
	private int				seekingTo;
	private volatile long	seekingSendTime;
	private float 			nextSeek	= -1;

	private int					pause_change_id_next;
	private boolean				pause_reported;
	private long 				pause_reported_time = -1;

	private int	pending_sleeps;
	private int	mute_count;

	//private String fileOpened;
	
	public void 
	doOpen(
		String 					fileOrUrl,
		final OutputConsumer	_outputConsumer )
	{
		synchronized( this ){
			
			if ( starting || started ){
				
				throw( new RuntimeException( "no can do" ));
			}
			
			starting = true;
		}
	
		final OutputConsumer output_consumer =
			new OutputConsumer()
			{
				boolean	latest = false;
				
				public void 
				consume(
					String output ) 
				{
					// System.out.println( output );
					
					boolean is_paused = output.startsWith( "ID_PAUSED" );
					
					if ( is_paused != latest ){
						
						updateObservedPaused( is_paused );
						
						latest = is_paused;
					}
						
					_outputConsumer.consume( output );
				}
			};
			
		try{
			
			//fileOpened = fileOrUrl;
			
			List<String> cmdList = new ArrayList<String>();
			
			cmdList.add( BINARY_PATH.getAbsolutePath());
			
			cmdList.add("-slave");
			
			//cmdList.add("-quiet");
			
			cmdList.add("-identify");
			
			cmdList.add("-prefer-ipv4");
			
			cmdList.add("-osdlevel");
			cmdList.add("0");
			
			/*if(Utils.isWindows()) {
				cmdList.add("-nofontconfig");
			}*/
			
			cmdList.add("-noautosub");
			
            if (OSUtils.isMacOSX()) {
                cmdList.add("-vo");
                cmdList.add("corevideo:buffer_name=fwmplayer");
            }
            
            if(OSUtils.isWindows()) {
                
                // setting video output driver mode.
                // NOTE:
                //  this is now a prioritized list of drives that mplayer will try, in order of priority, 
                //  until it finds one that works.  there is no need to parse output of mplayer unless we
                //  decide we want to block video output for cases other than direct3d on windows.
                cmdList.add("-vo");
                cmdList.add("direct3d,gl,directx,sdl");
                            	
                cmdList.add("-double");
                
                cmdList.add("-priority");
                cmdList.add("high");
                
                cmdList.add("-framedrop");
                
                cmdList.add("-wid");
                cmdList.add( String.valueOf(MPlayerMediator.instance().getCanvasComponentHwnd()));
                
                //workaround for mplayer on windows not being able to decode wma correctly with the wma demuxer.
                //by passing lavf it'll force mplayer to use ffmpeg's demuxer (libavformat).
                if (FilenameUtils.hasExtension(fileOrUrl, "wma","wmv","asf")) {
                    cmdList.add("-demuxer");
                    cmdList.add("lavf");
                }
            }
			
//			if(Utils.isWindows()) {
//				
//			} else {
//				cmdList.add(0,ShellUtilityFinder.getNice());
//				cmdList.add(1,"-n");
//				cmdList.add(2,"0");
//			}
//			
//			if(Utils.isWindows()) {
//				
//				cmdList.add("-ass");
//				cmdList.add("-ass-color");
//				cmdList.add("FFFFFF00");
//				cmdList.add("-ass-border-color");
//				cmdList.add("00000040");
//			}
			
//			Font font = Font.getFont("LiberationSans-Bold.ttf");
//			if(font != null) {			
//				cmdList.add("-font");
//				cmdList.add(font.getFontPath());
//				cmdList.add("-subfont-text-scale");
//				//cmdList.add(Utils.isWindows() ? "4" : "2.5");
//				cmdList.add("-subfont-blur");
//				cmdList.add("4");
//				cmdList.add("-subfont-outline");
//				cmdList.add("2");
//			}
//			
//			cmdList.add("-framedrop");
			
			//Set the volume at 0 as we load, as we don't want to hear sound before we resize / seek.
			//cmdList.add("-volume");
			//cmdList.add("0");
			
            if(OSUtils.isMacOSX() || OSUtils.isLinux()) {
            	    cmdList.add(fileOrUrl);
            } else if (OSUtils.isWindows()) {
            	    cmdList.add(String.format("\"%s\"", fileOrUrl));
            }
            
			String[] cmd = cmdList.toArray(new String[cmdList.size()]);
			String cmdString = Arrays.toString(cmd).replace(", ", " ");
			System.out.println(String.format("starting mplayer: %s", cmdString));
			
			try {
				//mPlayerProcess = Runtime.getRuntime().exec(cmd);
				ProcessBuilder pb = new ProcessBuilder(cmd);
				mPlayerProcess = pb.start();
				
				InputStream stdOut = mPlayerProcess.getInputStream();
				InputStream stdErr = mPlayerProcess.getErrorStream();
				OutputStream stdIn = mPlayerProcess.getOutputStream();
				
				final BufferedReader brStdOut = new BufferedReader(new InputStreamReader(stdOut));
				final BufferedReader brStdErr = new BufferedReader(new InputStreamReader(stdErr));
				final PrintWriter pwStdIn = new PrintWriter(new OutputStreamWriter(stdIn));
				
				Thread stdOutReader = new Thread("Player Console Out Reader") {
					public void run() {
						try {
							String line;
							while( (line = brStdOut.readLine()) != null) {
//								if ( LOG && !line.startsWith( "A:" )){
//									
//									System.out.println( "<- " + line );
//								}
								output_consumer.consume( line );
							}
						} catch (Exception e) {
							//e.printStackTrace();
						}
					};
				};
				stdOutReader.setDaemon(true);
				stdOutReader.start();
				
				Thread stdErrReader = new Thread("Player Console Err Reader") {
					public void run() {
						try {
							String line;
							while( (line = brStdErr.readLine()) != null) {
//								if ( LOG && !line.startsWith( "A:" )){
//									
//									System.out.println( "<- " + line );
//								}
								output_consumer.consume( line );
							}
						} catch (Exception e) {
							//e.printStackTrace();
						}
					};
				};
				stdErrReader.setDaemon(true);
				stdErrReader.start();
								
				Thread stdInWriter = new Thread("Player Console In Writer") {
					public void 
					run() 
					{
						try {
							while( true ){
							
								command_sem.reserve();

								String toBeSent;
								
								synchronized( MPlayerInstance.this ){
									
									if ( commands.isEmpty()){
										
										break;
									}
									
									toBeSent = commands.remove(0);
								}
								
								if ( LOG ){
									System.out.println( "-> " + toBeSent );
								}
									
								if ( toBeSent.startsWith( "sleep " ) || toBeSent.startsWith( "pausing_keep_force sleep " )){
									
									int	millis = Integer.parseInt( toBeSent.substring( toBeSent.startsWith( "p" )?25:6 ));
									
									try{
										Thread.sleep( millis );
										
									}catch( Throwable e ){
										
									}
																	
									synchronized( MPlayerInstance.this ){
									
										pending_sleeps -= millis;
									}
								}else if ( toBeSent.startsWith( "seek" ) || toBeSent.startsWith( "pausing_keep_force seek" )){
									
									seekingSendTime	= SystemTime.getMonotonousTime();
								}
								
								toBeSent = toBeSent.replaceAll("\\\\", "\\\\\\\\");
								
								pwStdIn.write(toBeSent + "\n");
							
								pwStdIn.flush();
							
							}
						}catch ( Throwable e ){
							
							e.printStackTrace();
							
						}finally{
							
							stop_sem.releaseForever();
						}
					};
				};
				stdInWriter.setDaemon(true);
				stdInWriter.start();
				
				
			}catch( Throwable e ){
				
				e.printStackTrace();
				
				stop_sem.releaseForever();
			}
		}finally{
			
			synchronized( this ){
				
				starting 	= false;
				started		= true;
				
				if ( stop_pending  ){
					
					doStop();
				}
			}
		}
	}

	protected void 
	sendCommand(
		String 	cmd,
		boolean pauseKeep )
	{
		synchronized( this ){
			
			if ( stopped ){
				
				return;
			}
			
			commands.add((pauseKeep ? "pausing_keep " : "" ) + cmd);
			
			command_sem.release();
		}
	}
	
	protected void 
	sendCommand(
		String cmd ) 
	{
		sendCommand( cmd, true );		
	}
	
	protected void 
	initialised()
	{
		synchronized( this ){
		
			//sendCommand("pause");
			
			sendCommand("get_property LENGTH");
			
			sendCommand("get_property SUB");
			
			sendCommand("get_property ASPECT");
			
			sendCommand("get_property WIDTH");
			
			sendCommand("get_property HEIGHT");
			
			sendCommand("get_property VOLUME");
			
			

		}
	}
		
	protected void
	updateObservedPaused(
		boolean	r_paused )
	{
		synchronized( this ){
			
			pause_reported 		= r_paused;
			pause_reported_time	= SystemTime.getMonotonousTime();
		}
	}
	
	private void
	pausedStateChanging()
	{
		final int delay = 333;
		
		pause_reported_time = -1;
		
		final int pause_change_id = ++pause_change_id_next;
		
		SimpleTimer.addEvent(
			"MP:PO",
			SystemTime.getOffsetTime( delay + pending_sleeps ),
			new TimerEventPerformer()
			{
				int	level = 0;
				
				public void 
				perform(
					TimerEvent event ) 
				{
					synchronized( MPlayerInstance.this ){
						
						if ( 	!stopped && 
								pause_change_id == pause_change_id_next &&
								level < 20){
							
							level++;
							
							if ( pause_reported_time >= 0 && pause_reported == paused ){
								
								return;
							}
							
							//System.out.println("pausedStateChanging() sending pause");						
							
							sendCommand( "pause", false );
							
							SimpleTimer.addEvent(
									"MP:PO2",
									SystemTime.getOffsetTime( delay + pending_sleeps ),
									this );
						}
					}
				}
			});
	}
	
	protected boolean 
	doPause() 
	{
		synchronized( this ){
			
			if ( paused ){
				
				return( false );
			}
			
			paused = true;
		
			pausedStateChanging();
			
			sendCommand( "pause", false );		

			return( true );
		}
	}

	
	protected boolean 
	doResume() 
	{
		synchronized( this ){

			if ( !paused ){
				
				return( false );
			}
			
			paused = false;
			
			pausedStateChanging();
			
			sendCommand( "pause", false );

			return( true );
		}
	}

	
	protected void 
	doSeek(
		float timeInSecs ) 
	{
		synchronized( this ){
		
			
			if( isSeeking ){
				
				nextSeek = timeInSecs;
				
			}else{
				
				isSeeking = true;

				nextSeek = -1;
				
				int value = (int)timeInSecs;
				
				seekingTo 		= value;
				
				seekingSendTime	= -1;
				
				sendCommand("seek " + value + " 2");
				
				sendCommand("get_time_pos");
			}
		}
	}

		/**
		 * this is called for every poisition received, not just after a seek
		 * @param time
		 */
	
	protected void
	positioned(
		float		time )
	{
		long	now = SystemTime.getMonotonousTime();
		
		synchronized( this ){
			
			if ( seekingSendTime == -1 ){
				
				return;
			}
			
			if( isSeeking ){
				
				if ( time >= seekingTo ){
					
					if ( 	now - seekingSendTime > 1000 ||
							time - seekingTo <= 2 ){
						
						positioned();
					}
				}
			}
		}
	}
	
		/**
		 * called to a specific position report
		 */
	
	protected void
	positioned()
	{
		synchronized( this ){
			
			if( isSeeking ){
			
				isSeeking 		= false;
				seekingSendTime = -1;
				
				if ( nextSeek != -1 ){
					
					doSeek( nextSeek );
				}
			}
		}
	}
	
	protected void 
	doSetVolume(
		int volume) 
	{
		synchronized( this ){
		
			sendCommand("volume " + volume + " 1");
		
			//sendCommand("get_property VOLUME");
		}
	}

	protected void
	doMute(
		boolean		on )
	{
		synchronized( this ){
			
			if ( on ){
				
				mute_count++;
				
				if ( mute_count == 1 ){
					
					sendCommand( "mute 1" );
				}
			}else{
				
				mute_count--;
				
				if ( mute_count == 0 ){
					
					if ( paused ){
						
							// slight hack : assume any queued seeks aren't required as if actioned
							// they will cause audio crap
						
						nextSeek = -1;
						
						pending_sleeps += 100;
						
						sendCommand( "sleep 100" );
					}
					
					sendCommand( "mute 0" );
				}
			}
		}
	}
	
	protected void 
	setAudioTrack(
		Language language) 
	{
		synchronized( this ){
		
			if ( language != null ){
				
				sendCommand("switch_audio " + language.getId());
			}
		}
	}
	
	private boolean redrawing;
	private long	redraw_completion;
	private long	redraw_last_frame;
	
	protected void
	doRedraw()
	{
		synchronized( this ){
		
			final int delay = 250;
			
			long	now = SystemTime.getMonotonousTime();
			
			redraw_completion = now + delay;

			if ( redrawing ){
				
				if ( now - redraw_last_frame > delay ){
					
					redraw_last_frame = now;
					
					sendCommand( "frame_step", false);
				}
			}else{
				
				doMute( true );
					
				redraw_last_frame = now;
				
				sendCommand( "frame_step", false);
		
				redrawing = true;
				
				SimpleTimer.addEvent(
					"MP:RD",
					SystemTime.getOffsetTime( delay ),
					new TimerEventPerformer()
					{
						public void 
						perform(
							TimerEvent event )
						{
							synchronized( MPlayerInstance.this ){
								
								long	now = SystemTime.getMonotonousTime();
								
								long	diff = redraw_completion - now;
								
								if ( diff < 0 || Math.abs( diff ) <= 25 ){
									
									redrawing = false;
									
									doMute( false );
									
								}else{
									
									SimpleTimer.addEvent(
										"MP:RD",
										SystemTime.getOffsetTime( diff ),
										this );
								}
							}
						}
					});
			}
		}
	}
	
	protected String 
	setSubtitles(
		Language language) 
	{
		synchronized( this ){
	
			String langId;
			String commandName = "sub_demux ";
			if(language != null) {
				langId = language.getId();
				if(language.getSource() == LanguageSource.FILE) {
					commandName = "sub_file ";
				}
				sendCommand("set_property sub_visibility 1");
			} else {
				sendCommand("set_property sub_visibility 0");
				return(null);
			}
						
			
			sendCommand(commandName + langId);
			return(langId);
		}
	}
	
	public volatile boolean activateNextSubtitleLoaded = false;
	
	protected void doLoadSubtitlesFile(String file, boolean autoPlay) {
		synchronized( this ){
			activateNextSubtitleLoaded = autoPlay;
			sendCommand("sub_load \"" + file + "\"");	
		}
	}
		
	public void 
	doStop() 
	{
		synchronized( this ){
			
			if ( starting ){
				
				stop_pending = true;
				
				return;
			}
			
			if ( stopped ){
				
				return;
			}
					
			sendCommand("stop");
			
			sendCommand("quit 0");
			
			stopped = true;
			
		}
		
		command_sem.release();
		
		if ( mPlayerProcess != null ){
			
			mPlayerProcess.destroy();
		}
					
		killProcesses( true );
		
		stop_sem.reserve();
	}
	
	private static void
	killProcesses(
		boolean	delay )
	{
		if ( Constants.isOSX ){

			String	process_name = BINARY_PATH.getName();
			
			if ( LOG ){
				System.out.println( "running killall -9 " + process_name );
			}
	
			if ( delay ){
				
				try{
					Thread.sleep(250);
					
				}catch( Throwable e ){
				}
			}
			
			runCommand(
					new String[]{
						"killall",
						"-9",
						process_name
					});
			
		}else if ( Constants.isWindows ){
			
			String	process_name = BINARY_PATH.getName();
			
			int	pos = process_name.lastIndexOf( "." );
			
			if ( pos != -1 ){
				
				process_name = process_name.substring( 0, pos );
			}
			
			if ( LOG ){
				System.out.println( "running tskill " + process_name );
			}
	
			if ( delay ){
				
				try{
					Thread.sleep(250);
					
				}catch( Throwable e ){
				}
			}
			
			runCommand(
					new String[]{
						"cmd",
						"/C",
						"tskill",
						process_name
					});
		}
	}
	
	private static void
	runCommand(
		String[]	command )
	{
		try{
			if ( !Constants.isWindows ){
			
				command[0] = findCommand( command[0] );
			}
			
			Runtime.getRuntime().exec( command ).waitFor();

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	private static String
	findCommand(
		String	name )
	{
		final String[]  locations = { "/bin", "/usr/bin" };

		for ( String s: locations ){

			File f = new File( s, name );

			if ( f.exists() && f.canRead()){

				return( f.getAbsolutePath());
			}
		}

		return( name );
	}
	
	protected interface
	OutputConsumer
	{
		public void
		consume(
			String		output );
	}

    public void doGetProperties(String fileOrUrl, final OutputConsumer _outputConsumer) {
        final OutputConsumer output_consumer = new OutputConsumer() {
            public void consume(String output) {
                _outputConsumer.consume(output);
            }
        };

        try {
            
            final CountDownLatch signal = new CountDownLatch(1);

            List<String> cmdList = new ArrayList<String>();

            cmdList.add(BINARY_PATH.getAbsolutePath());

            cmdList.add("-slave");
            cmdList.add("-identify");
            cmdList.add("-prefer-ipv4");
            cmdList.add("-osdlevel");
            cmdList.add("0");
            cmdList.add("-noautosub");
            cmdList.add("-vo");
            cmdList.add("null");
            cmdList.add("-ao");
            cmdList.add("null");
            cmdList.add("-frames");
            cmdList.add("0");

            cmdList.add(fileOrUrl);

            String[] cmd = cmdList.toArray(new String[cmdList.size()]);

            //COMMENT/UNCOMMENT THIS FOR TO SEE WHAT COMMAND IS BEING SENT TO MPLAYER
            //printCommand(cmd);

            try {
                InputStream stdOut = Runtime.getRuntime().exec(cmd).getInputStream();

                final BufferedReader brStdOut = new BufferedReader(new InputStreamReader(stdOut));

                Thread stdOutReader = new Thread("Player Console Out Reader") {
                    public void run() {
                        try {
                            String line;
                            while ((line = brStdOut.readLine()) != null) {
                                if (line.startsWith("ID_EXIT")) {
                                    signal.countDown();
                                    break;
                                }
                                output_consumer.consume(line);
                            }
                        } catch (Exception e) {
                            //e.printStackTrace();
                        }
                    };
                };
                stdOutReader.setDaemon(true);
                stdOutReader.start();
                
                signal.await(5, TimeUnit.SECONDS);

            } catch (Throwable e) {

                e.printStackTrace();
            }
        } finally {
        }
    }

    @SuppressWarnings("unused")
    private void printCommand(String[] cmd) {
        for (int i = 0; i < cmd.length; i++) {
            if (cmd[i].contains(" ")) {
                System.out.print("\"");
            }
            System.out.print(cmd[i]);
            if (cmd[i].contains(" ")) {
                System.out.print("\"");
            }
            System.out.print(" ");
        }
        System.out.println("\n");
    }
}