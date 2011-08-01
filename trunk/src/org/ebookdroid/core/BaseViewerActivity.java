package org.ebookdroid.core;

import org.ebookdroid.R;
import org.ebookdroid.core.events.CurrentPageListener;
import org.ebookdroid.core.events.DecodingProgressListener;
import org.ebookdroid.core.models.DecodingProgressModel;
import org.ebookdroid.core.models.DocumentModel;
import org.ebookdroid.core.models.ZoomModel;
import org.ebookdroid.core.multitouch.MultiTouchZoom;
import org.ebookdroid.core.settings.AppSettings;
import org.ebookdroid.core.views.PageViewZoomControls;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public abstract class BaseViewerActivity extends Activity implements IViewerActivity, DecodingProgressListener, CurrentPageListener
{
    private static final int DIALOG_GOTO = 0;
    private static final String DOCUMENT_VIEW_STATE_PREFERENCES = "DjvuDocumentViewState";

    private final AtomicReference<AppSettings> settings = new AtomicReference<AppSettings>();

    private IDocumentViewController documentController;
    private Toast pageNumberToast;
    private ZoomModel zoomModel;

    private FrameLayout frameLayout;

    private DecodingProgressModel progressModel;

    private MultiTouchZoom multiTouchZoom;

    private DocumentModel documentModel;



    /**
	 * Instantiates a new base viewer activity.
	 */
    public BaseViewerActivity() {
		super();
	}

	/**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        settings.set(new AppSettings(this));

        frameLayout = createMainContainer();

        setShowTitle();
        initView("");


    }

    private void initView(String password)
    {
    	DecodeService decodeService = createDecodeService();

        decodeService.setContentResolver(getContentResolver());
        final ViewerPreferences viewerPreferences = new ViewerPreferences(this);
        try
        {
        		decodeService.open(getIntent().getData(), password);
        }
        catch(Exception e)
        {
        	viewerPreferences.delRecent(getIntent().getData());

        	if(e.getMessage().equals("PDF needs a password!"))
        	{
        		setContentView(R.layout.password);
        		final Button ok = (Button) findViewById(R.id.pass_ok);
                ok.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                    	final EditText te = (EditText) findViewById(R.id.pass_req);
                    	initView(te.getText().toString());
                    }
                });
        		final Button cancel = (Button) findViewById(R.id.pass_cancel);
        		cancel.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                    	finish();
                    }
                });
        	}
        	else
        	{
        		setContentView(R.layout.error);
        		final TextView errortext = (TextView) findViewById(R.id.error_text);
        		errortext.setText(e.getMessage());
        		final Button cancel = (Button) findViewById(R.id.error_close);
        		cancel.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                    	finish();
                    }
                });

        	}
        	//Toast.makeText(this, e.getMessage(), 300).show();
        	return;
        }

        documentModel = new DocumentModel(decodeService);

        documentModel.addEventListener(this);


        zoomModel = new ZoomModel();
        initMultiTouchZoomIfAvailable();
        progressModel = new DecodingProgressModel();
        progressModel.addEventListener(this);

        viewerPreferences.addRecent(getIntent().getData());

        createDocumentView();

        frameLayout.addView(createZoomControls(zoomModel));
        setContentView(frameLayout);
        setProgressBarIndeterminateVisibility(false);
    }

    private void initMultiTouchZoomIfAvailable() {
        try {
            multiTouchZoom = ((MultiTouchZoom) Class.forName("org.ebookdroid.core.multitouch.MultiTouchZoomImpl").getConstructor(ZoomModel.class).newInstance(zoomModel));
        } catch (Exception e) {
            System.out.println("Multi touch zoom is not available: " + e);
        }
    }

    private void createDocumentView() {
        if (documentController != null) {
            frameLayout.removeView(documentController.getView());
            zoomModel.removeEventListener(documentController);
        }

        if (getAppSettings().getSinglePage()) {
            documentController = new SinglePageDocumentView(this);
        } else {
            documentController = new ContiniousDocumentView(this);
        }

        zoomModel.addEventListener(documentController);
        documentController.getView().setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));

        final SharedPreferences sharedPreferences = getSharedPreferences(DOCUMENT_VIEW_STATE_PREFERENCES, 0);
        documentController.goToPage(sharedPreferences.getInt(getIntent().getData().toString(), 0));
        documentController.showDocument();

        frameLayout.addView(documentController.getView());
    }


    public void decodingProgressChanged(final int currentlyDecoding)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
            	try {
            		setProgressBarIndeterminateVisibility(true);
            		getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS, currentlyDecoding == 0 ? 10000 : currentlyDecoding);
            	}
                catch (Throwable e) {
                }
            }
        });
    }

    public void currentPageChanged(int pageIndex)
    {
        final String pageText = (pageIndex + 1) + "/" + documentModel.getPageCount();
        if (pageNumberToast != null)
        {
            pageNumberToast.setText(pageText);
        }
        else
        {
            pageNumberToast = Toast.makeText(this, pageText, 300);
        }
        pageNumberToast.setGravity(Gravity.TOP | Gravity.LEFT,0,0);
        pageNumberToast.show();
        saveCurrentPage();
    }

    private void setWindowTitle()
    {
        final String name = getIntent().getData().getLastPathSegment();
        getWindow().setTitle(name);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
        setWindowTitle();
    }

    private void setShowTitle()
    {
    	 if (getAppSettings().getShowTitle())
         {
             getWindow().requestFeature(Window.FEATURE_NO_TITLE);
         }
         else
         {
        	 // Android 3.0+ you need both progress!!!
             getWindow().requestFeature(Window.FEATURE_PROGRESS);
             getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        	 setProgressBarIndeterminate(true);
         }
    }

    private void setFullScreen(AppSettings oldSettings, AppSettings newSettings)
    {
        if (oldSettings.getFullScreen() != newSettings.getFullScreen())
        {
            if (newSettings.getFullScreen())
            {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else
            {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
    }

    private PageViewZoomControls createZoomControls(ZoomModel zoomModel)
    {
        final PageViewZoomControls controls = new PageViewZoomControls(this, zoomModel);
        controls.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        zoomModel.addEventListener(controls);
        return controls;
    }

    private FrameLayout createMainContainer()
    {
        return new FrameLayout(this);
    }


    protected abstract DecodeService createDecodeService();

    @Override
    protected void onStop()
    {
        super.onStop();
    }


    private void setOrientation(AppSettings oldSettings, AppSettings newSettings)
    {
        if (oldSettings.getRotation() != newSettings.getRotation()) {
            setRequestedOrientation(newSettings.getRotation().getOrientation());
        }
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();

		AppSettings oldSettings = settings.getAndSet((new AppSettings(this)));
		onAppSettingsChanged(oldSettings, getAppSettings());
	}

	protected void onAppSettingsChanged(AppSettings oldSettings, AppSettings newSettings)
    {
        checkDocumentview(oldSettings, newSettings);
        setAlign(oldSettings, newSettings);
        setOrientation(oldSettings, newSettings);
        setFullScreen(oldSettings, newSettings);
    }

    private void setAlign(AppSettings oldSettings, AppSettings newSettings) {
		if (documentController != null && oldSettings.getPageAlign() != newSettings.getPageAlign()) {
			documentController.setAlign(newSettings.getPageAlign());
		}
	}

	/**
	 * Checks current type of document view and recreates if needed
	 */
    private void checkDocumentview(AppSettings oldSettings, AppSettings newSettings)
    {
        if (oldSettings.getSinglePage() != newSettings.getSinglePage())
        {
            createDocumentView();
        }

        if (documentController != null)
        {
          if (oldSettings.getPagesInMemory() != newSettings.getPagesInMemory()) {
            documentModel.setPagesInMemory(newSettings.getPagesInMemory());
          }
          if (oldSettings.getUseAnimation() != newSettings.getUseAnimation()) {
            documentController.updateUseAnimation();
          }
          documentController.updatePageVisibility();
        }
    }

	@Override
    protected void onDestroy() {
		if(documentModel != null)
		{
			documentModel.recycle();
			documentModel = null;
		}
	    super.onDestroy();
	}

    private void saveCurrentPage()
    {
        final SharedPreferences sharedPreferences = getSharedPreferences(DOCUMENT_VIEW_STATE_PREFERENCES, 0);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(getIntent().getData().toString(), documentModel.getCurrentPageIndex());
        editor.commit();
    }

    /**
	 * Called on creation options menu
	 *
	 * @param menu
	 *            the main menu
	 * @return true, if successful
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {

    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.mainmenu_exit:
                System.exit(0);
                return true;
            case R.id.mainmenu_goto_page:
                showDialog(DIALOG_GOTO);
                return true;
            case R.id.mainmenu_zoom:
            	zoomModel.toggleZoomControls();
            	return true;
            case R.id.mainmenu_outline:
            	final List<OutlineLink> outline = documentModel.getDecodeService().getOutline();
            	if((outline != null) && (outline.size()>0))
            	{
	            	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	            	final CharSequence[] items = outline.toArray(new CharSequence[outline.size()]);
	            	builder.setTitle("Outline");
	            	builder.setItems(items, new DialogInterface.OnClickListener() {
	            	    public void onClick(DialogInterface dialog, int item) {
	            	    	//Toast.makeText(getApplicationContext(), outline[item].getLink(), Toast.LENGTH_SHORT).show();
	            	    	final String link = outline.get(item).getLink();
	            	    	Log.d("VuDroid","Link: "+link);
	            	    	if(link.startsWith("#"))
	            	    	{
	            	    		 int pageNumber = 0;
	            	    	     try
	            	    	     {
	            	    	       	pageNumber = Integer.parseInt(link.substring(1).replace(" ",""));
	            	    	     }
	            	    	     catch(Exception e)
	            	    	     {
	            	    	        	pageNumber = 0;
	            	    	     }
	            	    	     if (pageNumber < 1 || pageNumber > documentModel.getPageCount())
	            	    	     {
	            	    	           Toast.makeText(getApplicationContext(), "Page number out of range. Valid range: 1-" + documentModel.getDecodeService().getPageCount(), 2000).show();
	            	    	           return;
	            	    	     }
	            	    		 documentController.goToPage(pageNumber-1);
	            	    	}
	            	    	else if(link.startsWith("http:"))
	            	    	{
	            	    		Intent i = new Intent(Intent.ACTION_VIEW);
	            	    		i.setData(Uri.parse(link));
	            	    		startActivity(i);
	            	    	}
	            	    }
	            	});
	            	AlertDialog alert = builder.create();
	            	alert.show();
            	}
            	else
            	{
            		Toast.makeText(getApplicationContext(), "Document without Outline", Toast.LENGTH_SHORT).show();
            	}
            	return true;
            case R.id.mainmenu_settings:
            	Intent i = new Intent(BaseViewerActivity.this, SettingsActivity.class);
    			startActivity(i);
            	return true;
            case R.id.mainmenu_nightmode:
              getAppSettings().switchNightMode();
            	getView().invalidate();
            	return true;
        }
        //return false;
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        switch (id)
        {
            case DIALOG_GOTO:
                return new GoToPageDialog(this);
        }
        return null;
    }

    /**
     * Gets the zoom model.
     *
     * @return the zoom model
     */
    public ZoomModel getZoomModel() {
        return zoomModel;
    }

    /**
     * Gets the multi touch zoom.
     *
     * @return the multi touch zoom
     */
    public MultiTouchZoom getMultiTouchZoom() {
        return multiTouchZoom;
    }

    public DecodeService getDecodeService() {
        return documentModel.getDecodeService();
    }

    /**
     * Gets the decoding progress model.
     *
     * @return the decoding progress model
     */
    public DecodingProgressModel getDecodingProgressModel() {
        return progressModel;
    }

    public DocumentModel getDocumentModel() {
        return documentModel;
    }

    public IDocumentViewController getDocumentController() {
        return documentController;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public View getView() {
        return documentController.getView();
    }


    @Override
    public AppSettings getAppSettings()
    {
       return settings.get();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
        }
    }

}
