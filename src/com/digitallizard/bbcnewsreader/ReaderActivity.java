/*******************************************************************************
 * BBC News Reader
 * Released under the BSD License. See README or LICENSE.
 * Copyright (c) 2011, Digital Lizard (Oscar Key, Thomas Boby)
 * All rights reserved.
 ******************************************************************************/
package com.digitallizard.bbcnewsreader;


import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.digitallizard.bbcnewsreader.data.DatabaseHandler;



public class ReaderActivity extends Activity {
	
	/* constants */
	static final int ACTIVITY_CHOOSE_CATEGORIES = 1;
	static final int CATEGORY_ROW_LENGTH = 4;
	static final int DIALOG_ERROR = 0;
	static final int NEWS_ITEM_DP_WIDTH = 70; //FIXME item width shouldn't be predefined
	
	/* variables */
	ScrollView scroller;

	private Messenger resourceMessenger;
	boolean resourceServiceBound;
	boolean loadInProgress;
	private DatabaseHandler database;
	LayoutInflater inflater; //used to create objects from the XML
	ImageButton refreshButton;
	TextView statusText;
	String[] categoryNames;
	ArrayList<TableLayout> physicalCategories;
	LinearLayout[][] physicalItems;
	int categoryRowLength; //the number of items to show per row
	Dialog errorDialog;
	boolean errorWasFatal;
	HashMap<String, Integer> itemIds;
	String[] itemNames = {"lorem", "ipsum", "dolor", "sit", "amet",
			"consectetuer", "adipiscing", "elit", "morbi", "vel",
			"ligula", "vitae", "arcu", "aliquet", "mollis",
			"etiam", "vel", "erat", "placerat", "ante",
			"porttitor", "sodales", "pellentesque", "augue",
			"purus", "lorem", "ipsum", "dolor", "sit", "amet",
			"consectetuer", "adipiscing", "elit", "morbi", "vel",
			"ligula", "vitae", "arcu", "aliquet", "mollis",
			"etiam", "vel", "erat", "placerat", "ante",
			"porttitor", "sodales", "pellentesque", "augue",
			"purus", "lorem", "ipsum", "dolor", "sit", "amet",
			"consectetuer", "adipiscing", "elit", "morbi", "vel",
			"ligula", "vitae", "arcu", "aliquet", "mollis",
			"etiam", "vel", "erat", "placerat", "ante",
			"porttitor", "sodales", "pellentesque", "augue",
			"purus","ligula", "vitae", "arcu", "aliquet", "mollis",
			"etiam", "vel", "erat", "placerat", "ante",
			"porttitor", "sodales", "pellentesque", "augue",
			"purus", "lorem", "ipsum", "dolor", "sit", "amet",
			"consectetuer", "adipiscing", "elit", "morbi", "vel",
			"ligula", "vitae", "arcu", "aliquet", "mollis",
			"etiam", "vel", "erat", "placerat", "ante",
			"porttitor", "sodales", "pellentesque", "augue",
			"purus"};
	

	/* service configuration */
	//the handler class to process new messages
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg){
			//decide what to do with the message
			switch(msg.what){
			case ResourceService.MSG_CLIENT_REGISTERED:
				break;
			case ResourceService.MSG_ERROR:
				Bundle bundle = msg.getData(); //retrieve the data
				errorOccured(bundle.getBoolean("fatal"), bundle.getString("msg"), bundle.getString("error"));
				break;
			case ResourceService.MSG_CATEOGRY_LOADED:
				categoryLoadFinished(msg.getData().getString("category"));
				break;
			case ResourceService.MSG_FULL_LOAD_COMPLETE:
				fullLoadComplete();
				break;
			case ResourceService.MSG_RSS_LOAD_COMPLETE:
				rssLoadComplete();
				break;
			default:
				super.handleMessage(msg); //we don't know what to do, lets hope that the super class knows
			}
		}
	}
	final Messenger messenger = new Messenger(new IncomingHandler()); //this is a target for the service to send messages to
	
	private ServiceConnection resourceServiceConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	    	Log.v(getLocalClassName(), "Service connected");
	        //this runs when the service connects
	    	resourceServiceBound = true; //flag the service as bound
	    	//save a pointer to the service to a local variable
	        resourceMessenger = new Messenger(service);
	        //try and tell the service that we have connected
	        //this means it will keep talking to us
	        sendMessageToService(ResourceService.MSG_REGISTER_CLIENT, null);
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        //this runs if the service randomly disconnects
	    	//if this happens there are more problems than a missing service
	        resourceMessenger = null; //as the service no longer exists, destroy its pointer
	    }
	};
    
    void errorOccured(boolean fatal, String msg, String error){
    	errorWasFatal = fatal; //so we know if we need to crash or not
    	//do we need to crash or not
    	if(fatal){
    		showErrorDialog("Fatal error:\n"+msg+"\nPlease try resetting the app.");
    		Log.e("BBC News Reader", "Error: "+msg);
    	}
    	else{
    		showErrorDialog("Error: "+msg);
    		Log.e("BBC News Reader", "Error:\n"+msg);
        	Log.e("BBC News Reader", "Oops something broke. Lets keep going.");
    	}
    }
    
    void showErrorDialog(String error){
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setMessage(error);
    	builder.setCancelable(false);
    	builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int id) {
                closeErrorDialog();
           }
    	});
    	errorDialog = builder.create();
    	errorDialog.show();
    }
    
    void closeErrorDialog(){
    	errorDialog = null; //destroy the dialog
    	//see if we need to end the program
    	if(errorWasFatal){
    		//crash out
    		Log.e("BBC News Reader", "Oops something broke. We'll crash now.");
        	System.exit(1); //closes the app with an error code
    	}
    }
    
    void loadData(){
    	//check we aren't currently loading news
    	if(!loadInProgress){
	    	//TODO display old news as old
	    	loadInProgress = true; //flag the data as being loaded
	    	//show the loading image on the button
	    	refreshButton.setImageDrawable(getResources().getDrawable(R.drawable.stop));
	    	//tell the user what is going on
	    	statusText.setText("Loading feeds...");
	    	//tell the service to load the data
	    	sendMessageToService(ResourceService.MSG_LOAD_DATA);
    	}
    }
    
    void stopDataLoad(){
    	//check we are actually loading news
    	if(loadInProgress){
    		//send a message to the service to stop it loading the data
    		sendMessageToService(ResourceService.MSG_STOP_DATA_LOAD);
    	}
    }
    
    void fullLoadComplete(){
    	//check we are actually loading news
    	if(loadInProgress){
	    	loadInProgress = false;
	    	//display the reloading image on the button
	    	refreshButton.setImageDrawable(getResources().getDrawable(R.drawable.refresh));
	    	//report the loaded status
	    	statusText.setText("Last updated ???");
	    	//tell the database to delete old items
	    	database.clearOld();
    	}
    }
    
    void rssLoadComplete(){
    	//check we are actually loading news
    	if(loadInProgress){
    		//tell the user what is going on
    		statusText.setText("Loading article texts...");
    	}
    }
    
    void doBindService(){
    	//load the resource service
    	bindService(new Intent(this, ResourceService.class), resourceServiceConnection, Context.BIND_AUTO_CREATE);
    	resourceServiceBound = true;
    }
    
    void doUnbindService(){
    	//disconnect the resource service
    	//check if the service is bound, if so, disconnect it
    	if(resourceServiceBound){
    		//politely tell the service that we are disconnected
    		sendMessageToService(ResourceService.MSG_UNREGISTER_CLIENT);
    		//remove local references to the service
    		unbindService(resourceServiceConnection);
    		resourceServiceBound = false;
    	}
    }
    
    void sendMessageToService(int what, Bundle bundle){
    	//check the service is bound before trying to send a message
    	if(resourceServiceBound){
	    	try{
				//create a message according to parameters
				Message msg = Message.obtain(null, what);
				//add the bundle if needed
				if(bundle != null){
					msg.setData(bundle);
				}
				msg.replyTo = messenger; //tell the service to reply to us, if needed
				resourceMessenger.send(msg); //send the message
			}
			catch(RemoteException e){
				//We are probably shutting down, but report it anyway
				Log.e("ERROR", "Unable to send message to service: " + e.getMessage());
			}
    	}
    }
    
    void sendMessageToService(int what){
    	sendMessageToService(what, null);
    }
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        loadInProgress = false;
        
        //load the database
        database = new DatabaseHandler(this);
        if(!database.isCreated()){
        	database.createTables();
        	database.addCategories();
        }
        
        //set up the inflater to allow us to construct layouts from the raw XML code
        inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        //make references to ui items
        refreshButton = (ImageButton) findViewById(R.id.refreshButton);
        statusText = (TextView) findViewById(R.id.statusText);
        
        createNewsDisplay();

        
        //start the service
        doBindService(); //loads the service
        //TODO start a refresh if we haven't refreshed recently
    }
    
    void createNewsDisplay(){
    	LinearLayout content = (LinearLayout)findViewById(R.id.newsScrollerContent); //a reference to the layout where we put the news
    	//clear the content area
    	content.removeAllViewsInLayout();
    	
    	//find the width and work out how many items we can add
    	int rowPixelWidth = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getWidth();
    	int rowWidth =  (int)Math.floor(rowPixelWidth / this.getResources().getDisplayMetrics().density); //formula to convert from pixels to dp
    	categoryRowLength = (int)Math.floor(rowWidth / NEWS_ITEM_DP_WIDTH);
    	
        //create the categories
        categoryNames = database.getEnabledCategories()[1]; //string array with category names in it
        physicalCategories = new ArrayList<TableLayout>(categoryNames.length);
        physicalItems = new LinearLayout[categoryNames.length][CATEGORY_ROW_LENGTH]; //the array to hold the news items
        physicalItems = new LinearLayout[categoryNames.length][categoryRowLength]; //the array to hold the news items
        itemIds = new HashMap<String, Integer>();
        //loop through adding category views
        for(int i = 0; i < categoryNames.length; i++){
        	//create the category
        	TableLayout category = (TableLayout)inflater.inflate(R.layout.list_category_item, null);
        	//change the name
        	TextView name = (TextView)category.findViewById(R.id.textCategoryName);
        	name.setText(categoryNames[i]);
        	//retrieve the row for the news items
        	TableRow newsRow = (TableRow)category.findViewById(R.id.rowNewsItem);
        	
        	//add some items to each category display
        	//loop through and add 4 physical news items
        	for(int t = 0; t < categoryRowLength; t++){
        		//add a new item to the display
        		LinearLayout item = (LinearLayout)inflater.inflate(R.layout.list_news_item, null);
        		physicalItems[i][t] = item; //store the item for future use
        		newsRow.addView(item); //add the item to the display
        	}
        	physicalCategories.add(i, category); //store the category for future use
        	content.addView(category); //add the category to the screen
        	
        	//populate this category with news
        	displayCategoryItems(i);
        }
    }
    
    void displayCategoryItems(int category){
    	//load from the database, if there's anything in it
    	String[][] items = database.getItems(categoryNames[category]);
    	if(items != null){
    		String[] titles = items[0];
    		String[] ids = items[3];
    		//change the physical items to match this
    		for(int i = 0; i < categoryRowLength; i++){
    			//check we have not gone out of range of the available news
    			if(i < titles.length){
    				TextView titleText = (TextView)physicalItems[category][i].findViewById(R.id.textNewsItemTitle);
    				titleText.setText(titles[i]);
    				//display an image for the item
    				ImageView imageView = (ImageView)physicalItems[category][i].findViewById(R.id.imageNewsItem);
    				//try and get an image for this item
    				byte[] imageBytes = database.getThumbnail(Integer.parseInt(ids[i]));
    				//check if any image data was returned
    				if(imageBytes != null){
    					//try to construct an image out of the bytes given by the database
    					Bitmap imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length); //load the image into a bitmap
    					imageView.setImageBitmap(imageBitmap);
    				}
    				else{
    					//set the image to the default "X"
    					imageView.setImageResource(R.drawable.no_thumb);
    				}
    				//save the ids for when an item is selected
    				itemIds.put((String)titleText.getText(), Integer.parseInt(ids[i]));
    			}
    		}
    	}
    }
    
    void categoryLoadFinished(String category){
    	//the database has finished loading a category, we can update
    	//FIXME very inefficient way to turn (string) name into (int) id
    	int id = 0; //the id of the client
    	for(int i = 0; i < categoryNames.length; i++){
    		//check if the name we have been given matches this category
    		if(category.equals(categoryNames[i]))
    			id = i;
    	}
    	displayCategoryItems(id); //redisplay this category
    }
    
    public boolean onCreateOptionsMenu(Menu menu){
    	super.onCreateOptionsMenu(menu);
    	//inflate the menu XML file
    	MenuInflater menuInflater = new MenuInflater(this);
    	menuInflater.inflate(R.layout.options_menu, menu);
    	return true; //we have made the menu so we can return true
    }
    
    protected void onDestroy(){
    	//disconnect the service
    	doUnbindService();
    	super.onDestroy(); //pass the destroy command to the super
    }
    
    public boolean onOptionsItemSelected(MenuItem item){
    	if(item.getTitle().equals("Choose Categories")){
    		//launch the category chooser activity
    		//create an intent to launch the next activity
        	Intent intent = new Intent(this, CategoryChooserActivity.class);
        	//load the boolean array of currently enabled categories
        	boolean[] categoryBooleans = database.getCategoryBooleans();
        	intent.putExtra("categorybooleans", categoryBooleans);
        	startActivityForResult(intent, ACTIVITY_CHOOSE_CATEGORIES);
    	}
    	if(item.getTitle().equals("Settings")){
    		showErrorDialog("Not implemented.");
    		//TODO add code to show the settings menu
    		//TODO add a settings menu
    	}
    	if(item.getTitle().equals("Reset")){
    		//clear the database tables and then crash out
    		//FIXME shouldn't crash on a table clear...
    		database.dropTables();
    		Log.w(this.getLocalClassName(), "Tables dropped. The app will now crash...");
    		System.exit(0);
    	}
    	return true; //we have received the press so we can report true
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data){
    	//wait for activities to send us result data
    	switch(requestCode){
    	case ACTIVITY_CHOOSE_CATEGORIES:
    		//check the request was a success
    		if(resultCode == RESULT_OK){
    			//TODO store the data sent back
    			database.setEnabledCategories(data.getBooleanArrayExtra("categorybooleans"));
    			//reload the ui
    			createNewsDisplay();
    		}
    		break;
    	}
    }
    
    public void refreshClicked(View item){
    	//Log.v("view", "width is: "+physicalCategories[1]].getWidth());
    	//start the load if we are not loading
    	if(!loadInProgress)
    		loadData();
    	else
    		stopDataLoad();
    }
    
    public void itemClicked(View item){
    	//retrieve the title of this activity
    	TextView titleText = (TextView)item.findViewById(R.id.textNewsItemTitle);
    	//check there is an item at this view
    	if(!titleText.getText().equals("No Title")){
    		//launch article view activity
    		Intent intent = new Intent(this, ArticleActivity.class);
	    	intent.putExtra("id", (int)itemIds.get(titleText.getText()));
	    	startActivity(intent);
    	}
    }
    
    public void categoryClicked(View view){
    	//FIXME there must be a more elegant way of doing this...
    	//get the parent of this view
    	TableLayout category = (TableLayout)(view.getParent());
    	//find the id of this category by looking it up in the list
    	int id = physicalCategories.indexOf(category);
    	//launch a new activity to show this category
    	Intent intent = new Intent(this, CategoryActivity.class);
    	intent.putExtra("title", categoryNames[id]);
    	startActivity(intent);
    }
}
