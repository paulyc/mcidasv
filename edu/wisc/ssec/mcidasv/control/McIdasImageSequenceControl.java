package edu.wisc.ssec.mcidasv.control;


import edu.wisc.ssec.mcidasv.data.McIdasXInfo;
import edu.wisc.ssec.mcidasv.data.McIdasXDataSource;
import edu.wisc.ssec.mcidasv.data.FrameDirtyInfo;

import java.awt.*;
import java.awt.event.*;

import java.io.*;

import java.net.URLEncoder;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import ucar.unidata.data.CompositeDataChoice;
import ucar.unidata.data.DataCancelException;
import ucar.unidata.data.DataChoice;
import ucar.unidata.data.DataContext;
import ucar.unidata.data.DataSelection;
import ucar.unidata.data.DataSourceImpl;
import ucar.unidata.data.DataUtil;
import ucar.unidata.data.grid.GridUtil;

import ucar.unidata.idv.ControlContext;
import ucar.unidata.idv.MapViewManager;
import ucar.unidata.idv.control.ImageSequenceControl;
import ucar.unidata.idv.control.WrapperWidget;
import ucar.unidata.idv.IntegratedDataViewer;
import ucar.unidata.idv.ViewManager;

//import ucar.unidata.ui.TextHistoryPane;
import ucar.unidata.ui.colortable.ColorTableManager;

import ucar.unidata.util.ColorTable;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.Misc;
import ucar.unidata.util.Trace;
import ucar.unidata.util.TwoFacedObject;

import ucar.visad.Util;
import ucar.visad.display.Animation;

import visad.*;
import visad.georef.MapProjection;


/**
 * A DisplayControl for handling McIDAS-X image sequences
 */
public class McIdasImageSequenceControl extends ImageSequenceControl {

    private mcCommandField commandLine;
    private JButton sendBtn;
    private JLabel runningThreads;
    
    private McIdasXInfo mcidasxInfo;

    private int ptSize = 12;
    private int threadCount = 0;

    private static DataChoice dc=null;
    private static Integer frmI;

    /** Holds frame component information */
    private FrameComponentInfo frameComponentInfo;
    private List frameDirtyInfoList = new ArrayList();
    private List frameNumbers = new ArrayList();

    /**
     * Default ctor; sets the attribute flags
     */
    public McIdasImageSequenceControl() {
        setAttributeFlags(FLAG_COLORTABLE | FLAG_DISPLAYUNIT);
        initFrameComponentInfo();
    }

    /**
     * Creates, if needed, the frameComponentInfo member.
     */
    private void initFrameComponentInfo() {
        if (this.frameComponentInfo == null) {
            this.frameComponentInfo = new FrameComponentInfo(true, true, true, false, true, false);
        }
    }
    
    /**
     * Initializes the frameDirtyInfoList member.
     */
    private void initFrameDirtyInfoList() {
    	this.frameDirtyInfoList.clear();
    	Integer frameNumber;
    	FrameDirtyInfo frameDirtyInfo;
    	for (int i=0; i<this.frameNumbers.size(); i++) {
    		frameNumber = (Integer)this.frameNumbers.get(i);
    		frameDirtyInfo = new FrameDirtyInfo(frameNumber.intValue(), false, false, false);
    		this.frameDirtyInfoList.add(frameDirtyInfo);
    	}
    }
    
    /**
     * Sets the frameDirtyInfoList member based on frame number
     */
    private void setFrameDirtyInfoList(int frameNumber, boolean dirtyImage, boolean dirtyGraphics, boolean dirtyColorTable) {
    	FrameDirtyInfo frameDirtyInfo;
    	for (int i=0; i<this.frameDirtyInfoList.size(); i++) {
    		frameDirtyInfo = (FrameDirtyInfo)frameDirtyInfoList.get(i);
    		if (frameDirtyInfo.getFrameNumber() == frameNumber) {
    			frameDirtyInfo.setDirtyImage(dirtyImage);
    			frameDirtyInfo.setDirtyGraphics(dirtyGraphics);
    			frameDirtyInfo.setDirtyColorTable(dirtyColorTable);
    			this.frameDirtyInfoList.set(i, frameDirtyInfo);
    		}
    	}
    }
    
    /**
     * Get the index by given frame number
     */
    private int getFrameIndexByNumber(int frameAsk) {
    	Integer frameNumber;
    	for (int i=0; i<this.frameNumbers.size(); i++) {
    		frameNumber = (Integer)this.frameNumbers.get(i);
    		if (frameNumber.intValue() == frameAsk) return(i);
    	}
    	return 0;
    }
    
    /**
     * Get the frame number by given index
     */
    private int getFrameNumberByIndex(int frameAsk) {
    	Integer frameNumber = 0;
    	if (0 <= frameAsk && frameAsk < frameNumbers.size()) {
    		frameNumber = (Integer)this.frameNumbers.get(frameAsk);
    	}
    	return frameNumber.intValue();
    }
    
    /**
     * Override the base class method that creates request properties
     * and add in the appropriate frame component request parameters.
     * @return  table of properties
     */
    protected Hashtable getRequestProperties() {
        Hashtable props = super.getRequestProperties();
        props.put(McIdasComponents.IMAGE, new Boolean(this.frameComponentInfo.getIsImage()));
        props.put(McIdasComponents.GRAPHICS, new Boolean(this.frameComponentInfo.getIsGraphics()));
        props.put(McIdasComponents.COLORTABLE, new Boolean(this.frameComponentInfo.getIsColorTable()));
        props.put(McIdasComponents.ANNOTATION, new Boolean(this.frameComponentInfo.getIsAnnotation()));
        props.put(McIdasComponents.FAKEDATETIME, new Boolean(this.frameComponentInfo.getFakeDateTime()));
        props.put(McIdasComponents.DIRTYINFO, this.frameDirtyInfoList);
        return props;
    }

    /**
     * Get control widgets specific to this control.
     *
     * @param controlWidgets   list of control widgets from other places
     *
     * @throws RemoteException  Java RMI error
     * @throws VisADException   VisAD Error
     */
    public void getControlWidgets(List controlWidgets)
        throws VisADException, RemoteException {

        super.getControlWidgets(controlWidgets);
        
        JPanel frameComponentsPanel =
            GuiUtils.hflow(Misc.newList(doMakeImageBox(), doMakeGraphicsBox(), doMakeAnnotationBox()), 2, 0);
        controlWidgets.add(
            new WrapperWidget( this, GuiUtils.rLabel("Frame components:"), frameComponentsPanel));

        JPanel frameBehaviorPanel =
        	GuiUtils.hflow(Misc.newList(doMakeResetProjectionBox(), doMakeFakeDateTimeBox()), 2, 0);
        controlWidgets.add(
        	new WrapperWidget( this, GuiUtils.rLabel("Frame behavior:"), frameBehaviorPanel));
                
        doMakeCommandField();
        getSendButton();
        getThreadsLabel();
        JPanel commandLinePanel =
            GuiUtils.hflow(Misc.newList(commandLine, sendBtn, runningThreads), 2, 0);
        controlWidgets.add(
            new WrapperWidget( this, GuiUtils.rLabel("Command Line:"), commandLinePanel));

        frmI = new Integer(0);
        ControlContext controlContext = getControlContext();
        List dss = ((IntegratedDataViewer)controlContext).getDataSources();
        McIdasXDataSource mds = null;
        List frameI = new ArrayList();
        for (int i=0; i<dss.size(); i++) {
          DataSourceImpl ds = (DataSourceImpl)dss.get(i);
          if (ds instanceof McIdasXDataSource) {
             frameNumbers.clear();
             mds = (McIdasXDataSource)ds;
             DataContext dataContext = mds.getDataContext();
             ColorTableManager colorTableManager = 
                 ((IntegratedDataViewer)dataContext).getColorTableManager();
             ColorTable ct = colorTableManager.getColorTable("MCIDAS-X");
             setColorTable(ct);
             this.mcidasxInfo = mds.getMcIdasXInfo();
             this.dc = getDataChoice();
             String choiceStr = this.dc.toString();
             if (choiceStr.equals("Frame Sequence")) {
            	 frameNumbers = mds.getFrameNumbers();
             } else {
                 StringTokenizer tok = new StringTokenizer(choiceStr);
                 String str = tok.nextToken();
                 if (str.equals("Frame")) {
                     frmI = new Integer(tok.nextToken());
                     frameNumbers.add(frmI);
                 } else {
                     frmI = new Integer(1);
                     frameNumbers.add(frmI);
                 }
             }
             break;
          }
       }
       setShowNoteText(true);
       noteTextArea.setRows(12);
       noteTextArea.setLineWrap(false);
       noteTextArea.setEditable(false);
       noteTextArea.setFont(new Font("Monospaced", Font.PLAIN, ptSize));
       
       initFrameDirtyInfoList();
       
       // Tell McIDAS-X to stop looping and go to a sensible frame
       if (frameNumbers.size() == 1) {
    	   sendCommandLine("TERM L OFF; SF " + frameNumbers.get(0), false);
           setNameFromUser("McIDAS-X Frame " + frameNumbers.get(0));
       }
       else {
    	   sendCommandLine("TERM L OFF; SF 1", false);
           setNameFromUser("McIDAS-X Frames " + frameNumbers.get(0) + "-" + frameNumbers.get(frameNumbers.size()-1));
       }
    }

    /**
     * Make the frame component check boxes.
     * @return Check box for Images
     */
    protected Component doMakeImageBox() {
        JCheckBox imageCbx = new JCheckBox("Image",frameComponentInfo.getIsImage());
        final boolean isImage = imageCbx.isSelected();
        imageCbx.setToolTipText("Set to import image data");
        imageCbx.addItemListener(new ItemListener() {
           public void itemStateChanged(ItemEvent e) {
              if (frameComponentInfo.getIsImage() != isImage) {
                 frameComponentInfo.setIsImage(isImage);
              } else {
                 frameComponentInfo.setIsImage(!isImage);
              }
              getRequestProperties();
              try {
                resetData();
              } catch (Exception ex) {
                System.out.println(ex);
                System.out.println("image exception");
              }
           }
        });
        return imageCbx;
    }

    /**
     * Make the frame component check boxes.
     * @return Check box for Graphics
     */
    protected Component doMakeGraphicsBox() {
        JCheckBox graphicsCbx = new JCheckBox("Graphics", frameComponentInfo.getIsGraphics());
        final boolean isGraphics = graphicsCbx.isSelected();
        graphicsCbx.setToolTipText("Set to import graphics data");
        graphicsCbx.addItemListener(new ItemListener() {
           public void itemStateChanged(ItemEvent e) {
              if (frameComponentInfo.getIsGraphics() != isGraphics) {
                 frameComponentInfo.setIsGraphics(isGraphics);
              } else {
                 frameComponentInfo.setIsGraphics(!isGraphics);
              }
              getRequestProperties();
              try {
                resetData();
              } catch (Exception ex) {
                System.out.println(ex);
                System.out.println("graphics exception");
              }
           }
        });
        return graphicsCbx;
    }

    /**
     * Make the frame component check boxes.
     * @return Check box for Color table
     */
    protected Component doMakeColorTableBox() {
        JCheckBox colorTableCbx = new JCheckBox("Color table", frameComponentInfo.getIsColorTable());
        final boolean isColorTable = colorTableCbx.isSelected();
        colorTableCbx.setToolTipText("Set to import color table data");
        colorTableCbx.addItemListener(new ItemListener() {
           public void itemStateChanged(ItemEvent e) {
              if (frameComponentInfo.getIsColorTable() != isColorTable) {
                 frameComponentInfo.setIsColorTable(isColorTable);
              } else {
                 frameComponentInfo.setIsColorTable(!isColorTable);
              }
              getRequestProperties();
              try {
                resetData();
              } catch (Exception ex) {
                System.out.println(ex);
                System.out.println("colortable exception");
              }
           }
        });
        return colorTableCbx;
    }
    
    /**
     * Make the frame component check boxes.
     * @return Check box for Annotation line
     */
    protected Component doMakeAnnotationBox() {
        JCheckBox annotationCbx = new JCheckBox("Annotation line", frameComponentInfo.getIsAnnotation());
        final boolean isAnnotation = annotationCbx.isSelected();
        annotationCbx.setToolTipText("Set to include image annotation line");
        annotationCbx.addItemListener(new ItemListener() {
           public void itemStateChanged(ItemEvent e) {
              if (frameComponentInfo.getIsAnnotation() != isAnnotation) {
                 frameComponentInfo.setIsAnnotation(isAnnotation);
              } else {
                 frameComponentInfo.setIsAnnotation(!isAnnotation);
              }
              getRequestProperties();
              try {
                resetData();
              } catch (Exception ex) {
                System.out.println(ex);
                System.out.println("annotation exception");
              }
           }
        });
        return annotationCbx;
    }
    
    /**
     * Make the frame behavior check boxes.
     * @return Check box for Projection reset
     */
    protected Component doMakeResetProjectionBox() {
        JCheckBox resetProjectionCbx = new JCheckBox("Reset projection", frameComponentInfo.getResetProjection());
        final boolean resetProjection = resetProjectionCbx.isSelected();
        resetProjectionCbx.setToolTipText("Set to reset projection when data is refreshed");
        resetProjectionCbx.addItemListener(new ItemListener() {
           public void itemStateChanged(ItemEvent e) {
              if (frameComponentInfo.getResetProjection() != resetProjection) {
                 frameComponentInfo.setResetProjection(resetProjection);
              } else {
                 frameComponentInfo.setResetProjection(!resetProjection);
              }
              getRequestProperties();
              try {
                resetData();
              } catch (Exception ex) {
                System.out.println(ex);
                System.out.println("reset projection exception");
              }
           }
        });
        return resetProjectionCbx;
    }

    /**
     * Make the frame behavior check boxes.
     * @return Check box for Fake date/time
     */
    protected Component doMakeFakeDateTimeBox() {
        JCheckBox fakeDateTimeCbx = new JCheckBox("Preserve frame order", frameComponentInfo.getFakeDateTime());
        final boolean fakeDateTime = fakeDateTimeCbx.isSelected();
        fakeDateTimeCbx.setToolTipText("Set to use fake date/time to preserve frame ordering");
        fakeDateTimeCbx.addItemListener(new ItemListener() {
           public void itemStateChanged(ItemEvent e) {
              if (frameComponentInfo.getFakeDateTime() != fakeDateTime) {
                 frameComponentInfo.setFakeDateTime(fakeDateTime);
              } else {
                 frameComponentInfo.setFakeDateTime(!fakeDateTime);
              }
              getRequestProperties();
              try {
                resetData();
              } catch (Exception ex) {
                System.out.println(ex);
                System.out.println("fake date/time exception");
              }
           }
        });
        return fakeDateTimeCbx;
    }
    
    protected void doMakeCommandField() {
        commandLine = new mcCommandField(30);
        commandLine.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                 String line = (commandLine.getText()).trim();
                 commandLine.setText("");
                 sendCommandLineThread(line, true);
            }
        });
    }
 
     protected void getSendButton() {
         sendBtn = new JButton("Send");
         sendBtn.addActionListener(new ActionListener() {
             public void actionPerformed(ActionEvent ae) {
                 String line = (commandLine.getText()).trim();
                 commandLine.setText("");
                 sendCommandLineThread(line, true);
             }
         });
    }
     
     private void getThreadsLabel() {
    	 runningThreads = GuiUtils.rLabel("Running: " + this.threadCount);
     }
     
     private void setThreadsLabel() {
    	 runningThreads.setText("Running: " + this.threadCount);
     }

     /**
      * Send the given commandline to McIDAS-X over the bridge
      * @param line
      * @param showprocess
      */
     private void sendCommandLine(String line, boolean showprocess) {
    	    	 
    	// Try to connect with the current animation display
    	IntegratedDataViewer theIdv=getIdv();
    	ViewManager theVM = theIdv.getViewManager();
    	Animation theAnimation = theVM.getAnimation();
    	int curIndex = theAnimation.getCurrent();
		int frameCur = getFrameNumberByIndex(curIndex);
//		System.out.println("Current animation index is " + Integer.toString(curIndex) + " (McIDAS frame " + frameCur + ")");
    	
        line = line.trim();
        if (line.length() < 1) return;
//        line = line.toUpperCase();
        String appendLine = line;
        try {
        	line = URLEncoder.encode(line,"UTF-8");
        } catch (Exception e) {
        	System.out.println("sendCommandLine URLEncoder exception: " + e);
        }
        
        DataInputStream inputStream = mcidasxInfo.getCommandInputStream(line, frameCur);
        if (!showprocess) {
            try { inputStream.close(); }
            catch (Exception e) {}
        	return;
        }
        appendTextLine(appendLine);
        try {
        	BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        	String responseType = null;
        	String ULine = null;
        	StringTokenizer tok;
        	boolean inList = false;
        	boolean doUpdate = false;
        	boolean dirtyImage, dirtyGraphics, dirtyColorTable;
        	// Burn the session key header line
        	String lineOut = br.readLine();
           	lineOut = br.readLine();
        	while (lineOut != null) {
//        		System.out.println("sendCommandLine processing: " + lineOut);
        		tok = new StringTokenizer(lineOut, " ");
        		responseType = tok.nextToken();
        		if (responseType.equals("U")) {
            		Integer frameInt = (Integer)Integer.parseInt(tok.nextToken());
            		inList = false;
            		dirtyImage = dirtyGraphics = dirtyColorTable = false;
            		// Don't pay attention to anything outside of our currently loaded frame list
    				for (int i=0; i<frameNumbers.size(); i++) {
    					if (frameInt.compareTo((Integer)frameNumbers.get(i)) == 0) inList = true;
    				}
    				if (inList) {
    					ULine = tok.nextToken();
//    					System.out.println("  Frame " + frameInt + " status line: " + ULine);
    					if (Integer.parseInt(ULine.substring(1,2)) != 0) {
//    						System.out.println("    Update image on frame " + frameInt);
    						dirtyImage = true;
    					}
    					if (Integer.parseInt(ULine.substring(3,4)) != 0) {
//    						System.out.println("    Update graphics on frame " + frameInt);
    						dirtyGraphics = true;
    					}
    					if (Integer.parseInt(ULine.substring(5,6)) != 0) {
//    						System.out.println("    Update colortable on frame " + frameInt);
    						dirtyColorTable = true;
    					}
    					if (dirtyImage || dirtyGraphics || dirtyColorTable) doUpdate = true;
    					setFrameDirtyInfoList(frameInt, dirtyImage, dirtyGraphics, dirtyColorTable);
    				}
                } else if (responseType.equals("T") ||
                		   responseType.equals("C")) {
                	appendTextLine("   " + lineOut.substring(6));
                	
                } else if (responseType.equals("M") ||
                           responseType.equals("S")) {
                    appendTextLine(" * " + lineOut.substring(6));
                	
                } else if (responseType.equals("R")) {
                	appendTextLine(" ! " + lineOut.substring(6));
                } else if (responseType.equals("V")) {
//                	System.out.println("Viewing frame status line: " + lineOut);
                	frameCur = Integer.parseInt(tok.nextToken());
        		} else if (responseType.equals("H") ||
     				       responseType.equals("K")) {
        			/* Don't do anything with these response types */
        		}
        		lineOut = br.readLine();
        	}
			if (doUpdate) {
				updateImage();
			}
			
	    	// Try to connect with the current animation display
			if (frameCur > 0) {
				int animationIndex = getFrameIndexByNumber(frameCur);
//				System.out.println("Setting animation to index " + animationIndex + " (McIDAS frame " + frameCur +")");
				theAnimation.setCurrent(animationIndex);
        	}
			
	    } catch (Exception e) {
	        System.out.println("sendCommandLine exception: " + e);
            try { inputStream.close(); }
            catch (Exception ee) {}
	    }

    }
     
     private void appendTextLine(String line) {
    	 noteTextArea.append(line + "\n");
    	 noteTextArea.setCaretPosition(noteTextArea.getDocument().getLength());
     }

    private void updateImage() {
        try {
        	getRequestProperties();
            resetData();
        } catch (Exception e) {
            System.out.println("updateImage exception: " + e);
        }
    }

    /**
     * This gets called when the control has received notification of a
     * dataChange event.
     * 
     * @throws RemoteException   Java RMI problem
     * @throws VisADException    VisAD problem
     */
    protected void resetData() throws VisADException, RemoteException {
//        MapProjection saveMapProjection;
//        if (frameDirtyInfo.dirtyImage) {
//          saveMapProjection = null;
//        } else {
//          saveMapProjection = getMapViewProjection();
//        }

        super.resetData();

    	if (frameComponentInfo.getResetProjection()) {
    		MapProjection mp = getDataProjection();
    		if (mp != null) {
    			MapViewManager mvm = getMapViewManager();
        		mvm.setMapProjection(mp, false); 
        	}
        }
    }
    
    /**
     * Try my hand at creating a thread
     */
    private class McIdasCommandLine implements Runnable {
    	private String line;
    	private boolean showprocess;
    	public McIdasCommandLine() {
    		this.line = "";
    		this.showprocess = true;
    	}
    	public McIdasCommandLine(String line, boolean showprocess) {
    		this.line = line;
    		this.showprocess = showprocess;
    	}
        public void run() {
        	notifyThreadStart();
        	sendCommandLine(this.line, this.showprocess);
        	notifyThreadStop();
        }
    }
    
    /**
     * Threaded sendCommandLine
     * @param line
     * @param showprocess
     */
    private void sendCommandLineThread(String line, boolean showprocess) {
    	McIdasCommandLine mcCmdLine = new McIdasCommandLine(line, showprocess);
    	Thread t = new Thread(mcCmdLine);
        t.start();
    }
    
    private void notifyThreadStart() {
    	this.threadCount++;
    	notifyThreadCount();
    }
    private void notifyThreadStop() {
    	this.threadCount--;
    	notifyThreadCount();
    }
    private void notifyThreadCount() {
    	setThreadsLabel();
    }
    
    /**
     * Extends JTextField to provide for swapped case similar to McIDAS
     */
    //TODO: Possibly add command history to this class using up (38) and down (40) keys
    private class mcCommandField extends JTextField {
    	
    	public mcCommandField(int columns) {
    		super(columns);
        }
    
        protected Document createDefaultModel() {
        	return new mcCommandDocument();
        }
        
        private class mcCommandDocument extends PlainDocument {
    
        	public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
        		if (str == null) return;
        			char[] upper = str.toCharArray();
        			for (int i = 0; i < upper.length; i++) {
        				if (Character.isLowerCase(upper[i])) {
        					upper[i] = Character.toUpperCase(upper[i]);
        				}
        				else {
            				upper[i] = Character.toLowerCase(upper[i]);	
        				}
        			}
        			super.insertString(offs, new String(upper), a);
    	      }
            
        }
    }
    
}
