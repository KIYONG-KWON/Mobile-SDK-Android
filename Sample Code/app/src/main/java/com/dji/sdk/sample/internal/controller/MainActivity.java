package com.dji.sdk.sample.internal.controller;

import android.animation.AnimatorInflater;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.model.ViewWrapper;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.DemoListView;
import com.dji.sdk.sample.internal.view.PresentableView;
import com.squareup.otto.Subscribe;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Stack;
import androidx.appcompat.app.AppCompatActivity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private FrameLayout contentFrameLayout;
    private ObjectAnimator pushInAnimator;
    private ObjectAnimator pushOutAnimator;
    private ObjectAnimator popInAnimator;
    private LayoutTransition popOutTransition;
    private ProgressBar progressBar;
    private Stack<ViewWrapper> stack;
    private TextView titleTextView;
    private SearchView searchView;
    private MenuItem searchViewItem;
    private MenuItem hintItem;
    TextView packetView, rcvIpText, rcvPortText; // 추가
    String rcvIp, rcvPort, rcvPacket;
    //private final Handler mHandler = new Handler();

    //데이터를 송신받을떄 실행할 코드
    private final Runnable rnb = new Runnable() {
        @Override
        public void run() {
            //레이아웃에 존재하는 UI를 불러옴
            //packetView = findViewById(R.id.packetView);
            //rcvIpText = findViewById(R.id.ipView);
            //rcvPortText = findViewById(R.id.portView);

            //UI에 UDP 수신 하면서 얻은 정보를 업데이트 함
            //.setText(rcvPacket);
            //rcvIpText.setText(rcvIp);
            //rcvPortText.setText(rcvPort);
        }
    };
    //region Life-cycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DJISampleApplication.getEventBus().register(this);
        setContentView(R.layout.activity_main);
        setupActionBar();
        contentFrameLayout = (FrameLayout) findViewById(R.id.framelayout_content);
        initParams();

        //UDP 수신 쓰레드 설정 및 시작부분
        ReceiveData rcvServer = new ReceiveData(9990);
        rcvServer.start();
    }

    @Override
    protected void onDestroy() {
        DJISampleApplication.getEventBus().unregister(this);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the options menu from XML
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchViewItem = menu.findItem(R.id.action_search);
        hintItem = menu.findItem(R.id.action_hint);
        searchView = (SearchView) searchViewItem.getActionView();
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false); // Do not iconify the widget; expand it by default
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                DJISampleApplication.getEventBus().post(new SearchQueryEvent(""));
                return false;
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                DJISampleApplication.getEventBus().post(new SearchQueryEvent(query));
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                DJISampleApplication.getEventBus().post(new SearchQueryEvent(newText));
                return false;
            }
        });

        // Hint click
        hintItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                showHint();
                return false;
            }
        });
        return true;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            Intent attachedIntent = new Intent();
            attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
            sendBroadcast(attachedIntent);
        }
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            popView();
        } else {
            super.onBackPressed();
        }
    }

    //endregion

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (null != actionBar) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.actionbar_custom);

            titleTextView = (TextView) (actionBar.getCustomView().findViewById(R.id.title_tv));
        }
    }


    private void setupInAnimations() {
        pushInAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.slide_in_right);
        pushOutAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.fade_out);
        popInAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.fade_in);
        ObjectAnimator popOutAnimator =
                (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.slide_out_right);

        pushOutAnimator.setStartDelay(100);

        popOutTransition = new LayoutTransition();
        popOutTransition.setAnimator(LayoutTransition.DISAPPEARING, popOutAnimator);
        popOutTransition.setDuration(popOutAnimator.getDuration());
    }

    private void initParams() {
        setupInAnimations();

        stack = new Stack<ViewWrapper>();
        View view = contentFrameLayout.getChildAt(0);
        stack.push(new ViewWrapper(view, R.string.activity_component_list));
    }

    private void pushView(ViewWrapper wrapper) {
        if (stack.size() <= 0) {
            return;
        }

        contentFrameLayout.setLayoutTransition(null);

        View showView = wrapper.getView();

        View preView = stack.peek().getView();

        stack.push(wrapper);

        if (showView.getParent() != null) {
            ((ViewGroup) showView.getParent()).removeView(showView);
        }
        contentFrameLayout.addView(showView);

        pushOutAnimator.setTarget(preView);
        pushOutAnimator.start();

        pushInAnimator.setTarget(showView);
        pushInAnimator.setFloatValues(contentFrameLayout.getWidth(), 0);
        pushInAnimator.start();

        refreshTitle();
        refreshOptionsMenu();
    }

    private void refreshTitle() {
        if (stack.size() > 1) {
            ViewWrapper wrapper = stack.peek();
            titleTextView.setText(wrapper.getTitleId());
        } else if (stack.size() == 1) {
            BaseProduct product = DJISampleApplication.getProductInstance();
            if (product != null && product.getModel() != null) {
                titleTextView.setText("" + product.getModel().getDisplayName());
            } else {
                titleTextView.setText(R.string.sample_app_name);
            }
        }
    }

    private void popView() {

        if (stack.size() <= 1) {
            finish();
            return;
        }

        ViewWrapper removeWrapper = stack.pop();

        View showView = stack.peek().getView();
        View removeView = removeWrapper.getView();

        contentFrameLayout.setLayoutTransition(popOutTransition);
        contentFrameLayout.removeView(removeView);

        popInAnimator.setTarget(showView);
        popInAnimator.start();

        refreshTitle();
        refreshOptionsMenu();
    }

    private void refreshOptionsMenu() {
        if (stack.size() == 2 && stack.peek().getView() instanceof DemoListView) {
            searchViewItem.setVisible(true);
        } else {
            searchViewItem.setVisible(false);
            searchViewItem.collapseActionView();
        }
        if (stack.size() == 3 && stack.peek().getView() instanceof PresentableView) {
            hintItem.setVisible(true);
        } else {
            hintItem.setVisible(false);
        }
    }


    private void showHint() {
        if (stack.size() != 0 && stack.peek().getView() instanceof PresentableView) {
            ToastUtils.setResultToToast(((PresentableView) stack.peek().getView()).getHint());
        }
    }


    //region Event-Bus
    @Subscribe
    public void onReceiveStartFullScreenRequest(RequestStartFullScreenEvent event) {
        getSupportActionBar().hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Subscribe
    public void onReceiveEndFullScreenRequest(RequestEndFullScreenEvent event) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getSupportActionBar().show();
    }

    @Subscribe
    public void onPushView(final ViewWrapper wrapper) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pushView(wrapper);
            }
        });
    }

    @Subscribe
    public void onConnectivityChange(ConnectivityChangeEvent event) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshTitle();
            }
        });
    }

    public static class SearchQueryEvent {
        private final String query;

        public SearchQueryEvent(String query) {
            this.query = query;
        }

        public String getQuery() {
            return query;
        }
    }

    public static class RequestStartFullScreenEvent {
    }

    public static class RequestEndFullScreenEvent {
    }

    public static class ConnectivityChangeEvent {
    }
    //endregion
    class ReceiveData extends Thread{
        //UDP로 패킷을 받았을때 받은 패킷을 UI로 올리기 위해 handler 받아옴
        //Handler handler = mHandler;
        DatagramSocket rSocket = new Socket("127.0.0.1",9990);

        //UDP 수신 포트를 열기(원하는 port 아무거나)
        public ReceiveData(int port){
            try {
                //수신 소켓 생성
                rSocket = new DatagramSocket(port);
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            try{
                while (true){
                    //받을 패킷 생성
                    byte[] buf = new byte[1024];
                    //패킷으로 변경 (바이트 버퍼, 버퍼길이)
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    //데이터 수신 대기
                    rSocket.receive(packet);
                    //패킷을 보낸 상대방의 ip를 저장함
                    InetAddress ina = packet.getAddress();
                    rcvIp = ina.toString();
                    //패킷을 보낸 상대방의 port를 저장함
                    int inp = packet.getPort();
                    rcvPort = String.valueOf(inp);
                    //수신받은 데이터를 문자열로 변환
                    rcvPacket = new String(buf);
                    //에코하기 위해 송신용 패킷을 만듦 (받은 패킷, 패킷 길이, 상대방 IP, 상대방 PORT)
                    packet = new DatagramPacket(buf, buf.length, ina, inp);
                    //이 기기로 UDP송신을 한 상대방에게 받은패킷을 돌려줌
                    rSocket.send(packet);
                    //데이터를 받았다면 UI로 표현해주기 위해 runnable 사용
                    //handler.post(rnb);
                    //쓰레드를 인터럽트로 종료시키기 위해 sleep을 사용함
                    sleep(20);
                }
            }catch (InterruptedException e){
                rcvPacket = e.toString();
                //handler.post(rnb);
            }
            catch (Exception e){
                rcvPacket = e.toString();
                //handler.post(rnb);
            }
        }
    }


}