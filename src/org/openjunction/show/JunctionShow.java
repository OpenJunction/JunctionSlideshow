package org.openjunction.show;

import java.net.URI;
import java.util.UUID;

import mobisocial.nfc.Nfc;

import org.json.JSONObject;

import edu.stanford.junction.JunctionException;
import edu.stanford.junction.JunctionMaker;
import edu.stanford.junction.SwitchboardConfig;
import edu.stanford.junction.android.AndroidJunctionMaker;
import edu.stanford.junction.api.activity.JunctionActor;
import edu.stanford.junction.api.messaging.MessageHeader;
import edu.stanford.junction.provider.xmpp.XMPPSwitchboardConfig;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;

public class JunctionShow extends Activity {
	private static final String TAG = "junction";
	private SwitchboardConfig mSwitchboardConfig;
	
	private Nfc mNfc;
	private int mNumSlides = -1;
	private int mCurSlide = 0;
	private String mPresentation;

	private final String[] mPresentationNames = new String[] { "accio nfc", "tedx" };
	private final String[] mPresentationUrls = new String[]
            { "http://prpl.stanford.edu/junction/show",
	          "http://prpl.stanford.edu/junction/tedx" };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mNfc = new Nfc(this);
        setContentView(R.layout.home);
        // TODO: junction binding

        new AlertDialog.Builder(this)
            .setTitle("Choose Presentation")
            .setItems(mPresentationNames, mOnPresentationSelected)
            .setOnCancelListener(mOnSelectionCancelled)
            .show();
        
    }

	private DialogInterface.OnClickListener mOnPresentationSelected
	        = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mPresentation = mPresentationUrls[which];
                    URI junctionUri = URI.create("junction://prpl.stanford.edu/" + UUID.randomUUID().toString().substring(0,8));
                    mSwitchboardConfig = new XMPPSwitchboardConfig("prpl.stanford.edu");
                    mJunctionBindingAsyncTask.execute(junctionUri);
                }
            };
    private DialogInterface.OnCancelListener mOnSelectionCancelled
            = new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            };
    
    public void onResume() {
    	super.onResume();
    	mNfc.onResume(this);
    }
    
    private void setNfc() {
    	// TODO: update js library!
    	//String invite = "http://prpl.stanford.edu/junction/show?jxinvite=" + URLEncoder.encode(mJunctionURI.toString(), "UTF-8");

    	String invite = mPresentation + "?jxsessionid=" + mController.getJunction().getSessionID();
    	invite += "&jxswitchboard=" + mController.getJunction().getSwitchboard();
    	
    	NdefRecord urlRecord = new NdefRecord(NdefRecord.TNF_ABSOLUTE_URI, NdefRecord.RTD_URI, new byte[] {}, invite.getBytes());
		NdefMessage ndef = new NdefMessage(new NdefRecord[] { urlRecord });
    	mNfc.share(ndef);
    }
    
    public void onPause() {
    	super.onPause();
    	mNfc.onPause(this);
    }
    
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ) {
        	mController.first();
        	return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
        	mController.last();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
    	if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true;
        }

        return super.onKeyLongPress(keyCode, event);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mController.prev();
            return true;
        }
        
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mController.next();
            return true;
        }

        return super.onKeyLongPress(keyCode, event);
    }
    
	private OnClickListener mNextListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			mController.next();
		}
	};
	
	private OnClickListener mPrevListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			mController.prev();
		}
	};
	
	private void setControllerView() {
		setContentView(R.layout.control);
		findViewById(R.id.prev).setOnClickListener(mPrevListener);
		findViewById(R.id.next).setOnClickListener(mNextListener);
	}
	
	private SlideshowController mController = new SlideshowController();
	private class SlideshowController extends JunctionActor {
		public SlideshowController() {
			super("controller");
		}
		public void onMessageReceived(MessageHeader header, JSONObject msg) {
			if (msg.has("joined") && "display".equalsIgnoreCase(msg.optString("joined"))) {
				if (JunctionShow.this.mNumSlides != -1) {
					cur();
					return;
				}
				JunctionShow.this.mNumSlides = msg.optInt("slidecount");
				JunctionShow.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						setControllerView();
					}
				});
			}
		};
		
		public void onActivityJoin() {
			setNfc();
		};

		public void cur() {
			try {
				JSONObject next = new JSONObject();
				next.put("action","jump");
				next.put("pos", mCurSlide);
				sendMessageToSession(next);
			} catch (Exception e) {
				Log.e(TAG, "Error sending action cur", e);
			}
		}
		
		public void next() {
			if (mCurSlide >= mNumSlides - 1) {
				return;
			}
			mCurSlide++;
			try {
				JSONObject next = new JSONObject();
				next.put("action","jump");
				next.put("pos", mCurSlide);
				sendMessageToSession(next);
			} catch (Exception e) {
				Log.e(TAG, "Error sending action next", e);
			}
		}
		
		public void prev() {
			if (mCurSlide <= 0 ) {
				return;
			}
			mCurSlide--;
			try {
				JSONObject next = new JSONObject();
				next.put("action", "jump");
				next.put("pos", mCurSlide);
				sendMessageToSession(next);
			} catch (Exception e) {
				Log.e(TAG, "Error sending action prev", e);
			}
		}
		
		public void first() {
			String action = "jump";
			try {
				JSONObject next = new JSONObject();
				next.put("action", action);
				next.put("pos", "first");
				mController.sendMessageToSession(next);
			} catch (Exception e) {
				Log.e(TAG, "Error sending action: " + action, e);
			}
		}
		
		public void last() {
			String action = "last";
			try {
				JSONObject next = new JSONObject();
				next.put("action", action);
				next.put("pos", "last");
				mController.sendMessageToSession(next);
			} catch (Exception e) {
				Log.e(TAG, "Error sending action: " + action, e);
			}
		}
	};
	
	private AsyncTask<URI, Void, Void> mJunctionBindingAsyncTask = new AsyncTask<URI, Void, Void>() {
		private ProgressDialog mDialog;
		
		
		protected Void doInBackground(URI... params) {
			JunctionMaker maker = AndroidJunctionMaker.getInstance(mSwitchboardConfig);
			
			try {
				// Connect to junction
				maker.newJunction(params[0], mController);
			} catch (JunctionException e) {
				e.printStackTrace();
			}
			return null;
		};
		
		@Override
		protected void onPreExecute() {
			if (mDialog == null) {
				mDialog = new ProgressDialog(JunctionShow.this);
				mDialog.setMessage("Loading slideshow...");
				mDialog.setIndeterminate(true);
				mDialog.setCancelable(true);
				mDialog.setOnCancelListener(
					new DialogInterface.OnCancelListener() {
						@Override
						public void onCancel(DialogInterface arg0) {
							
						}
				 	});
				
				mDialog.show();
			}	
		}
		
		protected void onPostExecute(Void result) {
			mDialog.hide();
		};
	};
	
	protected void onNewIntent(android.content.Intent intent) {
		mNfc.onNewIntent(this, intent);
	};
}