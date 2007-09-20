package edu.wisc.ssec.mcidasv.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTree;
import javax.swing.border.BevelBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import ucar.unidata.idv.IdvPersistenceManager;
import ucar.unidata.idv.IdvPreferenceManager;
import ucar.unidata.idv.IntegratedDataViewer;
import ucar.unidata.idv.ui.IdvUIManager;
import ucar.unidata.idv.ui.IdvWindow;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.Msg;
import ucar.unidata.util.TwoFacedObject;
import edu.wisc.ssec.mcidasv.StateManager;

/**
 * <p>Derive our own UI manager to do some specific things:
 * <ul>
 *   <li>Removing displays
 *   <li>Showing the dashboard
 *   <li>Adding toolbar customization options
 * </ul></p>
 * 
 * TODO: should probably change out the toolbar jpanels to be actual JToolbars
 */
public class UIManager extends IdvUIManager implements ActionListener {

	/** Separator to use between window title components. */
	protected static final String TITLE_SEPARATOR = " - ";
	
    /** The tag in the xml ui for creating the special example chooser */
    public static final String TAG_EXAMPLECHOOSER = "examplechooser";

    /** Label for the icons-only radio menu item. */
    private static final String LBL_TB_ICON_ONLY = "Display Only Icons";
    
    /** Label for the icons and labels radio menu item. */
    private static final String LBL_TB_ICON_TEXT = "Display Icons with Labels";
    
    /** Label for the labels-only radio menu item. */
    private static final String LBL_TB_TEXT_ONLY = "Display Only Labels";
    
    /** Label for the icon size check box menu item. */
    private static final String LBL_TB_ICON_TYPE = "Enable Large Icons";

    /** Label for the "link" to the toolbar customization preference tab. */
    private static final String LBL_TB_EDITOR = "Customize...";
    
    /** Action command for displaying only icons in the toolbar. */
    private static final String ACT_ICON_ONLY = "action.toolbar.onlyicons";
    
    /** Action command for displaying both icons and labels in the toolbar. */
    private static final String ACT_ICON_TEXT = "action.toolbar.iconsandtext";
    
    /** Action command for displaying only labels within the toolbar. */
    private static final String ACT_TEXT_ONLY = "action.toolbar.onlytext";
    
    /** Action command for manipulating the size of the toolbar icons. */
    private static final String ACT_ICON_TYPE = "action.toolbar.seticonsize";
    
    /** Action command for displaying the toolbar preference tab. */
    private static final String ACT_SHOW_PREF = "action.toolbar.showprefs";
    
    /** Action command for removing all displays */
    private static final String ACT_REMOVE_DISPLAYS = "action.displays.remove";
    
    /** Action command for showing the dashboard */
    private static final String ACT_SHOW_DASHBOARD = "action.dashboard.show";
        
    /** Handy reference to the name of the IDV toolbar customization tab. */
    private static final String TOOLBAR_TAB_NAME = "Toolbar";
    
    /** The IDV property that reflects the size of the icons. */
    private static final String PROP_ICON_SIZE = "idv.ui.iconsize";
    
    /**
     * Split window title using <tt>TITLE_SEPARATOR</tt>.
     * @param title The window title to split
     * @return Parts of the title with the white space trimmed. 
     */
    protected static String[] splitTitle(final String title) {
    	String[] splt = title.split(TITLE_SEPARATOR);
    	for (int i = 0; i < splt.length; i++) {
    		splt[i] = splt[i].trim();
    	}
    	return splt;
    }
    
    protected static String makeTitle(String window, String document) {
    	return window.concat(TITLE_SEPARATOR).concat(document);
    }
    
    
    /** Reference to the icon size checkbox for easy enabling/disabling. */
    private JCheckBoxMenuItem largeIconsEnabled;
    
    /** Whether or not icons should be displayed in the toolbar. */
    private boolean iconsEnabled = true;
    
    /** Whether or not labels should be displayed in the toolbar. */
    private boolean labelsEnabled = false;
    
    /** Reference to the current toolbar object. */
    //private JComponent toolbarUI;
    
    /** Constant that signifies a JRadioButtonMenuItem. */
    private static final int MENU_RADIO = 31337;
    
    /** Constant signifying a JCheckBoxMenuItem. */
    private static final int MENU_CHECKBOX = 31338;
    
    /** Constant signifying a JMenuItem. */
    private static final int MENU_NORMAL = 31339;
    
    /**
     * Reference to the toolbar container that the IDV is playing with.
     */
    private JComponent toolbar;
        
    /** 
     * <p>This array essentially serves as a friendly way to write the contents
     * of the toolbar customization popup menu. The layout of the popup menu
     * will basically look exactly like it does here in the code.</p> 
     * 
     * <p>Each item in the menu must have an action command, the Swing widget 
     * type, and then the String that'll let a user figure out what the widget
     * is supposed to do. The ordering is also important:<br/> 
     * <code>String action, int widgetType, String label.</code></p>
     * 
     * <p>If you'd like a separator to appear, simply make every part of the
     * entry null.</p>
     */
    private Object[][] types = {
    		{ACT_ICON_ONLY, MENU_RADIO, LBL_TB_ICON_ONLY},
    		{ACT_ICON_TEXT, MENU_RADIO, LBL_TB_ICON_TEXT},
    		{ACT_TEXT_ONLY, MENU_RADIO, LBL_TB_TEXT_ONLY},
    		{null, null, null},
    		{ACT_ICON_TYPE, MENU_CHECKBOX, LBL_TB_ICON_TYPE},
    		{null, null, null},
    		{ACT_SHOW_PREF, MENU_NORMAL, LBL_TB_EDITOR}
    };    
 
    private boolean addToolbarToWindowList = true;
    
    /**
     * Hands off our IDV instantiation to IdvUiManager.
     *
     * @param idv The idv
     */
    public UIManager(IntegratedDataViewer idv) {
        super(idv);
    }

    /**
     * Add in the menu items for the given display menu
     *
     * @param displayMenu The display menu
     */
    protected void initializeDisplayMenu(JMenu displayMenu) {
        JMenuItem mi;
        
        mi = new JMenuItem("Remove All Displays");
        mi.addActionListener(this);
        mi.setActionCommand(ACT_REMOVE_DISPLAYS);
        displayMenu.add(mi);
        displayMenu.addSeparator();                                                                                 
    	
        processBundleMenu(displayMenu,
                          IdvPersistenceManager.BUNDLES_FAVORITES);
        processBundleMenu(displayMenu, IdvPersistenceManager.BUNDLES_DISPLAY);

        processMapMenu(displayMenu, true);
        processStationMenu(displayMenu, true);
        processStandAloneMenu(displayMenu, true);
        
        Msg.translateTree(displayMenu);
    }
    
    /**
     * Add in the menu items for the given window menu
     *
     * @param windowMenu The window menu
     */
    public void makeWindowsMenu(JMenu windowMenu) {
        JMenuItem mi;
        boolean first = true;
        
        mi = new JMenuItem("Show Dashboard");
        mi.addActionListener(this);
        mi.setActionCommand(ACT_SHOW_DASHBOARD);
        windowMenu.add(mi);
        
        List windows = new ArrayList(IdvWindow.getWindows());
    	for (int i = 0; i < windows.size(); i++) {
    		final IdvWindow window = ((IdvWindow)windows.get(i));
    		// Skip the main window
    		if (window.getIsAMainWindow()) continue;
    		String title = window.getTitle();
    		String titleParts[] = title.split(" - ",2);
    		if (titleParts.length == 2) title = titleParts[1];
    		// Skip the dashboard
    		if (title.equals("Dashboard")) continue;
    		// Add a meaningful name if there is none
    		if (title.equals("")) title = "<Unnamed>";
    		if (window.isVisible()) {
    			mi = new JMenuItem(title);
    			mi.addActionListener(new ActionListener() {
    	            public void actionPerformed(ActionEvent ae) {
    	            	window.toFront();
    	            }
    	        });
				if (first) {
					windowMenu.addSeparator();
	    			first = false;
				}
    			windowMenu.add(mi);
    		}
    	}
        
        Msg.translateTree(windowMenu);
    }
        
    /** 
     * <p>Override to add some toolbar customization options to the JComponent 
     * returned by the IDV getToolbarUI method. The layout and menu items that 
     * appear within the customization menu are determined by the contents of 
     * <code>types</code> field.</p>
     *
     * <p>FIXME: doesn't trigger when a user right clicks over an icon!
	 * TODO: determine whether or not popup menu hides correctly.</p>
     * 
     * @see ucar.unidata.idv.ui.IdvUIManager#getToolbarUI()
     * 
	 * @return The modified version of the IDV toolbar.
     */
    public JComponent getToolbarUI() {
    	toolbar = super.getToolbarUI();
    	toolbar = GuiUtils.center(toolbar);
    	
    	JPopupMenu popup = new JPopupMenu();
    	ButtonGroup group = new ButtonGroup();
    	MouseListener popupListener = new PopupListener(popup);
    	    	
    	// time to create the toolbar customization menu.
    	for (int i = 0; i < types.length; i++) {
    		Object[] tempArr = types[i];
    		
    		// determine whether or not this entry is a separator. if it isn't,
    		// do some work and create the right types of menu items.
    		if (tempArr[0] != null) {
    			
    			JMenuItem item;
    			String action = (String)tempArr[0];
    			String label = (String)tempArr[2];
    			
    			int type = ((Integer)tempArr[1]).intValue();
    			
    			switch (type) {
    				case MENU_RADIO:
    					item = new JRadioButtonMenuItem(label);
    					group.add(item);
    					break;
    				
    				case MENU_CHECKBOX:
    					item = new JCheckBoxMenuItem(label);
    					if (action.startsWith(ACT_ICON_TYPE)) {
    						largeIconsEnabled = (JCheckBoxMenuItem)item;
    						
    						// make sure the previous selection persists
    						// across restarts.
    						String val = (String)getStateManager()
    							.getPreference(PROP_ICON_SIZE);
    						
    						if (val == null || val.equals("16"))
    							largeIconsEnabled.setState(false);
    						else
    							largeIconsEnabled.setState(true);
    					}
    					break;
    				
    				default:
    					// TODO: rethink this.
    					// this is intended to be the case that catches all the
    					// normal jmenuitems. I should probably rewrite this to
    					// look for a MENU_NORMAL flag or something.
    					item = new JMenuItem(label);
    					break;
    			}

    			item.addActionListener(this);
    			item.setActionCommand(action);
    			popup.add(item);
    		} else {
    			popup.addSeparator();
    		}
    	}
 
    	toolbar.addMouseListener(popupListener);

    	return toolbar;
    }

    /**
     * Need to override the IDV updateIconBar so we can preemptively add the
     * toolbar to the window manager (otherwise the toolbar won't update).
     */
    public void updateIconBar() {
    	if (addToolbarToWindowList == true && IdvWindow.getActiveWindow() != null) {
    		addToolbarToWindowList = false;
    		IdvWindow.getActiveWindow().addToGroup(IdvWindow.GROUP_TOOLBARS, toolbar);
    	}
    	
    	super.updateIconBar();
    }
        
    /**
     * Handles all the ActionEvents that occur for widgets contained within
     * this class. It's not so pretty, but it isolates the event handling in
     * one place (and reduces the number of action listeners to one).
     * 
     * @param e The event that triggered the call to this method.
     */
    public void actionPerformed(ActionEvent e) {
    	String cmd = (String)e.getActionCommand();
    	boolean toolbarEditEvent = false;
    	
    	// handle selecting the icon-only menu item
    	if (cmd.startsWith(ACT_ICON_ONLY)) {
    		iconsEnabled = true;
    		labelsEnabled = false;
    		largeIconsEnabled.setEnabled(true);
    		toolbarEditEvent = true;
    	} 
    	
    	// handle selecting the icon and label menu item
    	else if (cmd.startsWith(ACT_ICON_TEXT)) {
    		iconsEnabled = true;
    		labelsEnabled = true;
    		largeIconsEnabled.setEnabled(true);
    		toolbarEditEvent = true;
    	}
    	
    	// handle selecting the label-only menu item
    	else if (cmd.startsWith(ACT_TEXT_ONLY)) {
    		iconsEnabled = false;
    		labelsEnabled = false;
    		largeIconsEnabled.setEnabled(false);
    		toolbarEditEvent = true;
    	}
    	
    	// handle the user selecting the show toolbar preference menu item
    	else if (cmd.startsWith(ACT_SHOW_PREF)) {
    		IdvPreferenceManager prefs = getIdv().getPreferenceManager();
    		prefs.showTab(TOOLBAR_TAB_NAME);
    		toolbarEditEvent = true;
    	}
    	
    	// handle the user toggling the size of the icon
    	else if (cmd.startsWith(ACT_ICON_TYPE))
    		toolbarEditEvent = true;

    	// handle the user removing displays
    	else if (cmd.startsWith(ACT_REMOVE_DISPLAYS))
    		getIdv().removeAllDisplays();
    	
    	// handle popping up the dashboard.
    	else if (cmd.startsWith(ACT_SHOW_DASHBOARD))
    		showDashboard();
    	
    	else
    		System.err.println("Unsupported action event!");
    	
    	// if the user did something to change the toolbar, hide the current
    	// toolbar, replace it, and then make the new toolbar visible.
    	if (toolbarEditEvent == true) {
    		if (largeIconsEnabled.getState() == true)
    			getStateManager().writePreference(PROP_ICON_SIZE, "32");
    		else
    			getStateManager().writePreference(PROP_ICON_SIZE, "16");

    		updateIconBar();
    	}
    }
	
    /* (non-Javadoc)
     * @see ucar.unidata.idv.ui.IdvUIManager#about()
     */
    public void about() {

        JLabel iconLbl = new JLabel(
        	GuiUtils.getImageIcon(getIdv().getProperty(PROP_SPLASHICON, ""))
        );       
        
        StringBuffer mcVer = new StringBuffer();
        mcVer.append(((StateManager) getStateManager()).getMcIdasVersionAbout()+"<br>");
        mcVer.append("Based on IDV " + getStateManager().getVersionAbout()+"<br>");
        
        String text = mcVer.toString();
        JEditorPane editor = new JEditorPane();
        editor.setEditable(false);
        editor.setContentType("text/html");
        editor.setText(text);
        JPanel tmp = new JPanel();
        editor.setBackground(tmp.getBackground());
        editor.addHyperlinkListener(getIdv());

        JPanel contents = GuiUtils.topCenter(
        	GuiUtils.inset(iconLbl, 5),
            GuiUtils.inset(editor, 5)
        );
        contents.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        
        final JDialog dialog = GuiUtils.createDialog(
        	getFrame(),
            "About " + getStateManager().getTitle(),
            true
        );
        dialog.add(contents);
        JButton close = new JButton("Close");
        close.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				dialog.setVisible(false);
				dialog.dispose();
			}
        });
        
        JPanel bottom = new JPanel();
        bottom.add(close);
        
        dialog.add(GuiUtils.centerBottom(contents, bottom));
        dialog.pack();
        dialog.setLocationRelativeTo(getFrame());
        dialog.setVisible(true);

    }
    
	/**
	 * Handle mouse clicks that occur within the toolbar.  
	 */
    private class PopupListener extends MouseAdapter {
    	private JPopupMenu popup;
    	
    	public PopupListener(JPopupMenu p) {
    		popup = p;
    	}
    	
    	public void mousePressed(MouseEvent e) {
    		// isPopupTrigger is very nice. It varies depending upon whatever
    		// the norm is for the current platform.
    		if (e.isPopupTrigger()) {
    			popup.show(e.getComponent(), e.getX(), e.getY());
    		}
    	}    	
    }
    // end PopupListener. So many brackets!    
    
    public void showViewSelector(IdvWindow parent) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
        DefaultTreeModel model = new DefaultTreeModel(root);
    	final JTree tree = new JTree(model);
    	tree.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
    	tree.getSelectionModel().setSelectionMode(
    		TreeSelectionModel.SINGLE_TREE_SELECTION
    	);
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setIcon(null);
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        tree.setCellRenderer(renderer);
        
    	// create nodes from existing windows
    	for (IdvWindow window : (List<IdvWindow>)IdvWindow.getWindows()) {
    		if (window.getViewManagers().size() == 0) {
    			continue;
    		}
    		String[] titles = splitTitle(window.getTitle());
    		String label = titles.length > 1 ? titles[1] : titles[0];
    		DefaultMutableTreeNode displayNode = new DefaultMutableTreeNode(label);
    		List<ucar.unidata.idv.ViewManager> views = window.getViewManagers();
    		for (int i = 0; i < views.size(); i++) {
    			ucar.unidata.idv.ViewManager view = views.get(i);
    			String name = view.getName();
    			TwoFacedObject tfo = null;
    			if (name != null && name.length() > 0) {
    				tfo = new TwoFacedObject(name, view);
    			} else {
    				tfo = new TwoFacedObject("View " + (i+1), view);
    			}
    			displayNode.add(new DefaultMutableTreeNode(tfo));
    		}
    		root.add(displayNode);
    	}
    	
    	tree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent evt) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
				if (node == null || !(node.getUserObject() instanceof TwoFacedObject)) {
					return;
				}
				TwoFacedObject tfo = (TwoFacedObject) node.getUserObject();
				ucar.unidata.idv.ViewManager viewManager = (ucar.unidata.idv.ViewManager) tfo.getId();
				getIdv().getVMManager().setLastActiveViewManager(viewManager);
			}
    	});
    	
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandPath(tree.getPathForRow(i));
        }
    	
    	JOptionPane.showMessageDialog(
    		parent.getFrame(),
    		tree,
    		"Select a view",
    		JOptionPane.PLAIN_MESSAGE
    	);
    }
    
}