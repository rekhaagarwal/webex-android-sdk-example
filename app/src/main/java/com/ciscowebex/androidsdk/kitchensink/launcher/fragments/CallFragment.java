/*
 * Copyright 2016-2017 Cisco Systems Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package com.ciscowebex.androidsdk.kitchensink.launcher.fragments;


import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Rational;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.ciscowebex.androidsdk.WebexError;
import com.ciscowebex.androidsdk.kitchensink.R;
import com.ciscowebex.androidsdk.kitchensink.actions.WebexAgent;
import com.ciscowebex.androidsdk.kitchensink.actions.commands.AddCallHistoryAction;
import com.ciscowebex.androidsdk.kitchensink.actions.commands.RequirePermissionAction;
import com.ciscowebex.androidsdk.kitchensink.actions.commands.ToggleSpeakerAction;
import com.ciscowebex.androidsdk.kitchensink.actions.events.AnswerEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.DialEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.HangupEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.OnAuxStreamEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.OnCallMembershipEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.OnConnectEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.OnDisconnectEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.OnMediaChangeEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.OnRingingEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.OnWaitingEvent;
import com.ciscowebex.androidsdk.kitchensink.actions.events.PermissionAcquiredEvent;
import com.ciscowebex.androidsdk.kitchensink.launcher.LauncherActivity;
import com.ciscowebex.androidsdk.kitchensink.service.AwakeService;
import com.ciscowebex.androidsdk.kitchensink.service.FloatWindowService;
import com.ciscowebex.androidsdk.kitchensink.ui.BaseFragment;
import com.ciscowebex.androidsdk.kitchensink.ui.FullScreenSwitcher;
import com.ciscowebex.androidsdk.kitchensink.ui.ParticipantsAdapter;
import com.ciscowebex.androidsdk.people.Person;
import com.ciscowebex.androidsdk.phone.AuxStream;
import com.ciscowebex.androidsdk.phone.CallMembership;
import com.ciscowebex.androidsdk.phone.CallObserver;
import com.ciscowebex.androidsdk.phone.MediaRenderView;
import com.ciscowebex.androidsdk.phone.MultiStreamObserver;
import com.ciscowebex.androidsdk.phone.internal.CallImpl;
import com.github.benoitdion.ln.Ln;
import com.squareup.picasso.Picasso;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;

import static com.ciscowebex.androidsdk.kitchensink.actions.events.WebexAgentEvent.postEvent;
import static com.ciscowebex.androidsdk.phone.CallObserver.RemoteSendingSharingEvent;
import static com.ciscowebex.androidsdk.phone.CallObserver.SendingSharingEvent;

/**
 * A simple {@link BaseFragment} subclass.
 */
public class CallFragment extends BaseFragment {
    protected static final int MEDIA_PROJECTION_REQUEST = 2;
    private static final String CALLEE = "callee";
    private static final String INCOMING_CALL = "incoming";
    private WebexAgent agent;
    private FullScreenSwitcher screenSwitcher;
    private boolean isConnected = false;
    private HashMap<View, AuxStreamViewHolder> mAuxStreamViewMap = new HashMap<>();
    private HashMap<String, Person> mIdPersonMap = new HashMap<>();
    private boolean isFloatingBind = false;

    @BindView(R.id.localView)
    View localView;

    @BindView(R.id.remoteView)
    View remoteView;

    @BindView(R.id.viewRemoteAvatar)
    ImageView remoteAvatar;

    @BindView(R.id.screenShare)
    View screenShare;

    @BindView(R.id.view_call_control)
    View viewCallControl;

    @BindView(R.id.view_aux_videos_container)
    View viewAuxVideosContainer;

    @BindView(R.id.view_aux_videos)
    GridLayout viewAuxVideos;

    @BindView(R.id.view_participants)
    RecyclerView viewParticipants;

    @BindView(R.id.buttonHangup)
    Button buttonHangup;

    @BindView(R.id.buttonDTMF)
    Button buttonDTMF;

    @BindView(R.id.switchLoudSpeaker)
    Switch switchLoudSpeaker;

    @BindView(R.id.switchSendVideo)
    Switch switchSendingVideo;

    @BindView(R.id.switchSendAudio)
    Switch switchSendingAudio;

    @BindView(R.id.switchReceiveVideo)
    Switch switchReceiveVideo;

    @BindView(R.id.switchReceiveAudio)
    Switch switchReceiveAudio;

    @BindView(R.id.radioFrontCam)
    RadioButton radioFrontCam;

    @BindView(R.id.radioBackCam)
    RadioButton radioBackCam;

    @BindView(R.id.call_layout)
    ConstraintLayout layout;

    @BindView(R.id.switchShareContent)
    Switch switchShareContent;

    @BindView(R.id.keypad)
    View keypad;

    @BindView(R.id.floatButton)
    ImageView floatButton;

    @BindView(R.id.tab_callcontrol)
    TextView tabCallControl;

    @BindView(R.id.tab_aux_video)
    TextView tabAuxVideo;

    @BindView(R.id.tab_participants)
    TextView tabParticipants;

    private ParticipantsAdapter participantsAdapter;
    private Snackbar snackbar;

    // Required empty public constructor

    class AuxStreamViewHolder {
        View item;
        MediaRenderView mediaRenderView;
        ImageView viewAvatar;
        TextView textView;

        AuxStreamViewHolder(View item) {
            this.item = item;
            this.mediaRenderView = item.findViewById(R.id.view_video);
            this.viewAvatar = item.findViewById(R.id.view_avatar);
            this.textView = item.findViewById(R.id.name);
        }
    }

    public CallFragment() {
    }

    public static CallFragment newAnswerCallInstance() {
        return CallFragment.newInstance(INCOMING_CALL);
    }

    public static CallFragment newInstance(String id) {
        CallFragment fragment = new CallFragment();
        Bundle args = new Bundle();
        args.putInt(LAYOUT, R.layout.fragment_call);
        args.putString(CALLEE, id);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        Drawable drawableCallControl = getResources().getDrawable(R.drawable.ic_file_word, null);
        Drawable drawableAuxVideo = getResources().getDrawable(R.drawable.ic_file_excel, null);
        Drawable drawableParticipants = getResources().getDrawable(R.drawable.ic_file_zip, null);
        Rect bounds = new Rect(0, 0, 120, 120);
        drawableCallControl.setBounds(bounds);
        drawableAuxVideo.setBounds(bounds);
        drawableParticipants.setBounds(bounds);
        tabCallControl.setCompoundDrawables(null, drawableCallControl, null, null);
        tabAuxVideo.setCompoundDrawables(null, drawableAuxVideo, null, null);
        tabParticipants.setCompoundDrawables(null, drawableParticipants, null, null);

        agent = WebexAgent.getInstance();
        screenSwitcher = new FullScreenSwitcher(getActivity(), layout, remoteView);
        updateScreenShareView();
        if (participantsAdapter == null) {
            participantsAdapter = new ParticipantsAdapter(null);
            participantsAdapter.setOnLetInClickListener(new ParticipantsAdapter.OnLetInClickListener() {
                @Override
                public void onLetInClick(ParticipantsAdapter.CallMembershipEntity entity) {
                    for (CallMembership callMembership : agent.getActiveCall().getMemberships()) {
                        if (callMembership.getPersonId().equals(entity.getPersonId())) {
                            agent.getActiveCall().letIn(callMembership);
                            break;
                        }
                    }

                }
            });
            viewParticipants.setAdapter(participantsAdapter);
        }
        if (!isConnected) {
            setViewAndChildrenEnabled(layout, false);
            ((SurfaceView) localView).setZOrderMediaOverlay(true);
            ((SurfaceView) screenShare).setZOrderMediaOverlay(true);
            requirePermission();
        }
    }

    private static void setViewAndChildrenEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setViewAndChildrenEnabled(child, enabled);
            }
        }
    }

    private void setupWidgetStates() {
        switch (agent.getDefaultCamera()) {
            case FRONT:
                radioFrontCam.setChecked(true);
                break;
            case BACK:
                radioBackCam.setChecked(true);
                break;
            case CLOSE:
                localView.setVisibility(View.GONE);
                break;
        }
        switchLoudSpeaker.setChecked(agent.getSpeakerPhoneOn());
        switchSendingVideo.setChecked(agent.isSendingVideo());
        switchSendingAudio.setChecked(agent.isSendingAudio());
        switchReceiveVideo.setChecked(agent.isReceivingVideo());
        switchReceiveAudio.setChecked(agent.isReceivingAudio());

        updateScreenShareView();
    }

    private void updateScreenShareView() {
        screenShare.setVisibility(agent.isScreenSharing() ? View.VISIBLE : View.INVISIBLE);
    }

    private void requirePermission() {
        new RequirePermissionAction(getActivity()).execute();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PermissionAcquiredEvent event) {
        makeCall();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private void setButtonsEnable(boolean enable) {
        buttonHangup.setEnabled(enable);
        buttonDTMF.setEnabled(false);
    }

    @OnClick(R.id.buttonHangup)
    public void onHangup() {
        agent.hangup();
        ((LauncherActivity) getActivity()).goBackStack();
    }

    @OnClick(R.id.buttonDTMF)
    public void onDTMF() {
        if (isConnected)
            keypad.setVisibility(keypad.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    @OnClick({R.id.digit_0, R.id.digit_1, R.id.digit_2,
            R.id.digit_3, R.id.digit_4, R.id.digit_5,
            R.id.digit_6, R.id.digit_7, R.id.digit_8,
            R.id.digit_9, R.id.digit_hash,
            R.id.digit_asterisk})
    public void sendDTMF(View view) {
        if (isConnected) {
            String keyPressed = (String) view.getTag();
            agent.getActiveCall().sendDTMF(keyPressed, result -> {
            });
        }
    }

    @OnClick(R.id.remoteView)
    public void onRemoteViewClicked() {
        if (remoteAvatar.getVisibility() == View.VISIBLE) return;
        screenSwitcher.toggleFullScreen();
        updateFullScreenLayout();
    }

    private void updateFullScreenLayout() {
        updateScreenShareView();
        ((SurfaceView) remoteView).setZOrderMediaOverlay(screenSwitcher.isFullScreen());
        localView.setVisibility(screenSwitcher.isFullScreen() ? View.GONE : View.VISIBLE);
    }

    @OnCheckedChanged({R.id.switchSendVideo, R.id.switchSendAudio,
            R.id.switchReceiveVideo, R.id.switchReceiveAudio, R.id.switchShareContent})
    public void onSwitchCallAbility(Switch s) {
        switch (s.getId()) {
            case R.id.switchSendVideo:
                if (radioBackCam.isChecked())
                    agent.setBackCamera();
                else {
                    radioFrontCam.setChecked(true);
                    agent.setFrontCamera();
                }
                agent.sendVideo(s.isChecked());
                break;
            case R.id.switchSendAudio:
                agent.sendAudio(s.isChecked());
                break;
            case R.id.switchReceiveVideo:
                agent.receiveVideo(s.isChecked());
                break;
            case R.id.switchReceiveAudio:
                agent.receiveAudio(s.isChecked());
                break;
            case R.id.switchShareContent:
                if (s.isChecked()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel mChannel = new NotificationChannel("screen_share_notification_channel", "screen_share_notification_channel", NotificationManager.IMPORTANCE_HIGH);
                        NotificationManager notifyManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                        notifyManager.createNotificationChannel(mChannel);
                    }
                    Notification notification = new NotificationCompat.Builder(getActivity(), "screen_share_notification_channel")
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("Cisco Kitchensink")
                            .setContentText("Sharing screen to others")
                            .setTicker("Screen Sharing")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setDefaults(Notification.DEFAULT_SOUND)
                            .build();

                    agent.getActiveCall().startSharing(notification, 0xabcde, r -> {
                        Ln.d("startSharing result: " + r);
                        if (!r.isSuccessful()) {
                            switchShareContent.setChecked(false);
                        }
                    });
                } else
                    agent.getActiveCall().stopSharing(r -> {
                        Ln.d("stopSharing result: " + r);
                    });
                break;

        }
    }

    @OnCheckedChanged(R.id.switchLoudSpeaker)
    public void onSwitchLoudSpeakerChanged(Switch s) {
        new ToggleSpeakerAction(getActivity(), (CallImpl) agent.getActiveCall(), s.isChecked()).execute();
    }

    @OnClick(R.id.radioBackCam)
    public void onBackCamRadioClicked() {
        agent.setBackCamera();
    }

    @OnClick(R.id.radioFrontCam)
    public void onFrontCamRadioClicked() {
        agent.setFrontCamera();
    }

    @OnClick({R.id.tab_callcontrol, R.id.tab_aux_video, R.id.tab_participants})
    public void onTabClick(View view) {
        switch (view.getId()) {
            case R.id.tab_callcontrol:
                viewCallControl.setVisibility(View.VISIBLE);
                viewAuxVideosContainer.setVisibility(View.GONE);
                viewParticipants.setVisibility(View.GONE);
                break;
            case R.id.tab_aux_video:
                viewCallControl.setVisibility(View.GONE);
                viewAuxVideosContainer.setVisibility(View.VISIBLE);
                viewParticipants.setVisibility(View.GONE);
                break;
            case R.id.tab_participants:
                viewCallControl.setVisibility(View.GONE);
                viewAuxVideosContainer.setVisibility(View.GONE);
                viewParticipants.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    @OnClick(R.id.floatButton)
    void showFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPicInPic();
        } else if (checkFloatPermission(getActivity())) {
            startFloating();
        } else
            requestSettingCanDrawOverlays();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void enterPicInPic() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        Rational aspectRatio = new Rational(remoteView.getWidth(), remoteView.getHeight());
        builder.setAspectRatio(aspectRatio);
        getActivity().enterPictureInPictureMode(builder.build());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        floatButton.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        localView.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
    }

    private ServiceConnection floatingConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            FloatWindowService.MyBinder myBinder = (FloatWindowService.MyBinder) binder;
            FloatWindowService service = myBinder.getService();
            service.setAgent(agent);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private void startFloating() {
        if (!isFloatingBind) {
            Intent intent = new Intent(getActivity(), FloatWindowService.class);
            isFloatingBind = getActivity().getApplicationContext().bindService(intent, floatingConnection, Context.BIND_AUTO_CREATE);
            getActivity().moveTaskToBack(true);
        }
    }

    private void stopFloating() {
        if (isFloatingBind) {
            getActivity().getApplicationContext().unbindService(floatingConnection);
            isFloatingBind = false;
            new Handler().postDelayed(() -> agent.setVideoRenderViews(new Pair<>(localView, remoteView)), 500);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        stopFloating();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        screenSwitcher.updateOnRotation();
        updateScreenShareView();
    }

    @Override
    public void onBackPressed() {
        if (isConnected)
            agent.hangup();
    }

    private void makeCall() {
        String callee = getCallee();
        if (callee.isEmpty())
            return;

        if (callee.equals(INCOMING_CALL)) {
            setButtonsEnable(false);
            agent.answer(localView, remoteView, screenShare, false, null);
            return;
        }
        agent.startPreview(localView);
        agent.dial(callee, localView, remoteView, screenShare, false, null);
        new AddCallHistoryAction(callee, "out").execute();
        setButtonsEnable(true);
    }

    private String getCallee() {
        Bundle bundle = getArguments();
        return bundle != null ? bundle.getString(CALLEE) : "";
    }

    private void feedback() {
        BaseFragment fm = new CallFeedbackFragment();
        ((LauncherActivity) getActivity()).replace(fm);
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(DialEvent event) {
        if (!event.isSuccessful()) {
            if (event.getError() != null && event.getError().getErrorCode() == WebexError.ErrorCode.HOST_PIN_OR_MEETING_PASSWORD_REQUIRED.getCode()) {
                showPasswordDialog();
            } else if (event.getError() != null && event.getError().getErrorCode() == WebexError.ErrorCode.VIEW_H264_LICENSE.getCode()) {
                Toast.makeText(getActivity(), "View license, stop dial", Toast.LENGTH_SHORT).show();
                feedback();
            } else {
                Toast.makeText(getActivity(), "Dial failed!", Toast.LENGTH_SHORT).show();
                feedback();
            }
        }
    }

    private void showPasswordDialog() {
        EditText etKey = new EditText(getActivity());
        etKey.setHint("Host Key");
        EditText etPassword = new EditText(getActivity());
        etPassword.setHint("Meeting Password");
        LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(etKey);
        layout.addView(etPassword);
        new AlertDialog.Builder(getActivity())
                .setTitle("Are you the host?")
                .setMessage("If you are the host, please enter host key. Otherwise, enter the meeting password.")
                .setView(layout)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String key = etKey.getText().toString();
                    String password = etPassword.getText().toString();
                    if (TextUtils.isEmpty(key) && TextUtils.isEmpty(password)) {
                        Toast.makeText(getActivity(), "Please enter key or password.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(password)) {
                        Toast.makeText(getActivity(), "Please only enter key or password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean isModerator;
                    String PIN;
                    if (!TextUtils.isEmpty(key)) {
                        isModerator = true;
                        PIN = key;
                    } else {
                        isModerator = false;
                        PIN = password;
                    }
                    String callee = getCallee();
                    if (callee.isEmpty())
                        return;
                    if (callee.equals(INCOMING_CALL)) {
                        setButtonsEnable(false);
                        agent.answer(localView, remoteView, screenShare, isModerator, PIN);
                        return;
                    }
                    agent.dial(callee, localView, remoteView, screenShare, isModerator, PIN);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(getActivity(), "Dial failed!", Toast.LENGTH_SHORT).show();
                    feedback();
                })
                .show();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(AnswerEvent event) {
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(HangupEvent event) {
        setButtonsEnable(false);
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(OnRingingEvent event) {
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(OnWaitingEvent event) {
        String text = "Waiting in lobby:" + event.waitReason.name();
        if (snackbar != null) {
            snackbar.setText(text);
        } else
            snackbar = Snackbar.make(layout, text, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(OnConnectEvent event) {
        isConnected = true;
        startAwakeService();
        floatButton.setVisibility(View.VISIBLE);
        setViewAndChildrenEnabled(layout, true);
        if (agent.getDefaultCamera().equals(WebexAgent.CameraCap.CLOSE))
            agent.sendVideo(false);
        setupWidgetStates();
        updateParticipants();
        event.call.setMultiStreamObserver(new MultiStreamObserver() {
            @Override
            public void onAuxStreamChanged(AuxStreamChangedEvent event) {
                postEvent(new OnAuxStreamEvent(event));
            }

            @Override
            public View onAuxStreamAvailable() {
                Ln.d("onAuxStreamAvailable");
                View auxStreamView = LayoutInflater.from(getActivity()).inflate(R.layout.remote_video_view, null);
                AuxStreamViewHolder auxStreamViewHolder = new AuxStreamViewHolder(auxStreamView);
                mAuxStreamViewMap.put(auxStreamViewHolder.mediaRenderView, auxStreamViewHolder);
                return auxStreamViewHolder.mediaRenderView;
            }

            @Override
            public View onAuxStreamUnavailable() {
                Ln.d("onAuxStreamUnavailable");
                return null;
            }
        });
        if (snackbar != null)
            snackbar.dismiss();
    }

    private void updateParticipants() {
        if (agent == null || agent.getActiveCall() == null) return;
        List<CallMembership> callMemberships = agent.getActiveCall().getMemberships();
        if (callMemberships == null) return;
        Ln.d("updateParticipants: " + callMemberships.size());
        for (CallMembership membership : callMemberships) {
            String personId = membership.getPersonId();
            if (/*membership.getState() != CallMembership.State.JOINED || */personId == null || personId.isEmpty() || membership.getDisplayName() == null || membership.getDisplayName().isEmpty())
                continue;
            participantsAdapter.addOrUpdateItem(new ParticipantsAdapter.CallMembershipEntity(personId, membership.getDisplayName(), "", membership.isSendingAudio(), membership.isSendingVideo(), membership.getState()));
            agent.getWebex().people().get(personId, r -> {
                if (r == null || !r.isSuccessful() || r.getData() == null) return;
                mIdPersonMap.put(personId, r.getData());
                updatePersonInfoForParticipants(personId, r.getData());
            });
        }
    }

    private void updatePersonInfoForParticipants(String personId, Person person) {
        participantsAdapter.updateName(personId, person.getDisplayName());
        participantsAdapter.updateAvatar(personId, person.getAvatar());
    }

    private void updatePersonInfoForActiveSpeaker(String personId, Person person) {
        if (participantsAdapter.getActiveSpeaker() == null || person == null || !participantsAdapter.getActiveSpeaker().equals(personId))
            return;
        String avatar = person.getAvatar();
        if (avatar == null || avatar.isEmpty()) {
            remoteAvatar.setImageResource(R.drawable.google_contacts_android);
        } else {
            Picasso.with(getActivity()).cancelRequest(remoteAvatar);
            Picasso.with(getActivity()).load(avatar).fit().into(remoteAvatar);
        }
    }

    private void updatePersonInfoForAuxStream(String personId, Person person, AuxStreamViewHolder auxStreamViewHolder) {
        if (personId == null || personId.isEmpty() || person == null || auxStreamViewHolder == null)
            return;
        auxStreamViewHolder.textView.setText(person.getDisplayName());
        String avatar = person.getAvatar();
        if (avatar == null || avatar.isEmpty()) {
            auxStreamViewHolder.viewAvatar.setImageResource(R.drawable.google_contacts_android);
        } else {
            Picasso.with(getActivity()).cancelRequest(auxStreamViewHolder.viewAvatar);
            Picasso.with(getActivity()).load(avatar).fit().into(auxStreamViewHolder.viewAvatar);
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(OnDisconnectEvent event) {
        isConnected = false;
        if (isFloatingBind)
            return;
        keypad.setVisibility(View.GONE);
        stopAwakeService();
        floatButton.setVisibility(View.GONE);
        if (agent.getActiveCall() == null || event.getCall().equals(agent.getActiveCall())) {
            mAuxStreamViewMap.clear();
            mIdPersonMap.clear();
            feedback();
        }
        if (snackbar != null)
            snackbar.dismiss();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onEventMainThread(OnMediaChangeEvent event) {
        if (event.callEvent instanceof CallObserver.RemoteSendingVideoEvent) {
            Ln.d("RemoteSendingVideoEvent: " + ((CallObserver.RemoteSendingVideoEvent) event.callEvent).isSending());
        } else if (event.callEvent instanceof RemoteSendingSharingEvent) {
            Ln.d("RemoteSendingSharingEvent: " + ((RemoteSendingSharingEvent) event.callEvent).isSending());
            if (((RemoteSendingSharingEvent) event.callEvent).isSending()) {
                event.callEvent.getCall().setVideoRenderViews(new Pair<>(localView, screenShare));
                event.callEvent.getCall().setSharingRenderView(remoteView);
            } else {
                event.callEvent.getCall().setSharingRenderView(null);
                event.callEvent.getCall().setVideoRenderViews(new Pair<>(localView, remoteView));
            }
            updateScreenShareView();
        } else if (event.callEvent instanceof SendingSharingEvent) {
            Ln.d("SendingSharingEvent: " + ((SendingSharingEvent) event.callEvent).isSending());
            if (((SendingSharingEvent) event.callEvent).isSending()) {
//                sendNotification();
                backToHome();
            }
        } else if (event.callEvent instanceof CallObserver.ActiveSpeakerChangedEvent) {
            CallMembership membership = ((CallObserver.ActiveSpeakerChangedEvent) event.callEvent).to();
            Ln.d("ActiveSpeakerChangedEvent: " + membership);
            if (membership != null && membership.getPersonId() != null && !membership.getPersonId().isEmpty()) {
                String personId = membership.getPersonId();
                participantsAdapter.updateActiveSpeaker(personId);
                if (membership.isSendingVideo()) {
                    remoteAvatar.setVisibility(View.GONE);
                } else {
                    remoteAvatar.setVisibility(View.VISIBLE);
                    Person person = mIdPersonMap.get(personId);
                    if (person == null) {
                        remoteAvatar.setImageResource(R.drawable.google_contacts_android);
                        agent.getWebex().people().get(personId, r -> {
                            if (!r.isSuccessful() || r.getData() == null) return;
                            mIdPersonMap.put(personId, r.getData());
                            updatePersonInfoForActiveSpeaker(personId, r.getData());
                        });
                    } else {
                        String avatar = person.getAvatar();
                        if (avatar == null || avatar.isEmpty()) {
                            remoteAvatar.setImageResource(R.drawable.google_contacts_android);
                        } else {
                            Picasso.with(getActivity()).cancelRequest(remoteAvatar);
                            Picasso.with(getActivity()).load(avatar).fit().into(remoteAvatar);
                        }
                    }
                }
            } else {
                remoteAvatar.setVisibility(View.VISIBLE);
                remoteAvatar.setImageResource(android.R.color.darker_gray);
            }
        }
    }


    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(OnAuxStreamEvent event) {
        Ln.d("OnAuxStreamEvent: " + event.callEvent.getAuxStream());
        AuxStream auxStream = event.callEvent.getAuxStream();
        if (event.callEvent instanceof MultiStreamObserver.AuxStreamOpenedEvent) {
            MultiStreamObserver.AuxStreamOpenedEvent openEvent = (MultiStreamObserver.AuxStreamOpenedEvent) event.callEvent;
            if (openEvent.isSuccessful()) {
                Ln.d("AuxStreamOpenedEvent successful");
                viewAuxVideos.addView(mAuxStreamViewMap.get(openEvent.getRenderView()).item);
            } else {
                Ln.d("AuxStreamOpenedEvent failed: " + openEvent.getError());
                mAuxStreamViewMap.remove(openEvent.getRenderView());
            }
        } else if (event.callEvent instanceof MultiStreamObserver.AuxStreamClosedEvent) {
            MultiStreamObserver.AuxStreamClosedEvent closeEvent = (MultiStreamObserver.AuxStreamClosedEvent) event.callEvent;
            if (closeEvent.isSuccessful()) {
                Ln.d("AuxStreamClosedEvent successful");
                AuxStreamViewHolder auxStreamViewHolder = mAuxStreamViewMap.get(closeEvent.getRenderView());
                mAuxStreamViewMap.remove(closeEvent.getRenderView());
                viewAuxVideos.removeView(auxStreamViewHolder.item);
            } else {
                Ln.d("AuxStreamClosedEvent failed: " + closeEvent.getError());
            }
        } else if (event.callEvent instanceof MultiStreamObserver.AuxStreamSendingVideoEvent) {
            Ln.d("AuxStreamSendingVideoEvent: " + auxStream.isSendingVideo());
            AuxStreamViewHolder auxStreamViewHolder = mAuxStreamViewMap.get(auxStream.getRenderView());
            if (auxStreamViewHolder == null) return;
            if (auxStream.isSendingVideo()) {
                auxStreamViewHolder.viewAvatar.setVisibility(View.GONE);
            } else {
                CallMembership membership = auxStream.getPerson();
                if (membership == null || membership.getPersonId() == null || membership.getPersonId().isEmpty())
                    return;
                String personId = membership.getPersonId();
                auxStreamViewHolder.viewAvatar.setVisibility(View.VISIBLE);
                Person person = mIdPersonMap.get(personId);
                if (person == null) {
                    auxStreamViewHolder.viewAvatar.setImageResource(R.drawable.google_contacts_android);
                    agent.getWebex().people().get(personId, r -> {
                        if (!r.isSuccessful() || r.getData() == null) return;
                        mIdPersonMap.put(personId, r.getData());
                        updatePersonInfoForAuxStream(personId, r.getData(), auxStreamViewHolder);
                    });
                } else {
                    String avatar = person.getAvatar();
                    if (avatar == null || avatar.isEmpty()) {
                        auxStreamViewHolder.viewAvatar.setImageResource(R.drawable.google_contacts_android);
                    } else {
                        Picasso.with(getActivity()).cancelRequest(auxStreamViewHolder.viewAvatar);
                        Picasso.with(getActivity()).load(avatar).fit().into(auxStreamViewHolder.viewAvatar);
                    }
                }
            }
        } else if (event.callEvent instanceof MultiStreamObserver.AuxStreamPersonChangedEvent) {
            Ln.d("AuxStreamPersonChangedEvent: " + auxStream.getPerson());
            AuxStreamViewHolder auxStreamViewHolder = mAuxStreamViewMap.get(auxStream.getRenderView());
            if (auxStream.getPerson() == null) {
                mAuxStreamViewMap.remove(auxStream.getRenderView());
                viewAuxVideos.removeView(auxStreamViewHolder.item);
            } else {
                CallMembership membership = auxStream.getPerson();
                if (membership == null || membership.getPersonId() == null || membership.getPersonId().isEmpty())
                    return;
                String personId = membership.getPersonId();
                participantsAdapter.updateSendingAudioStatus(personId, membership.isSendingAudio());
                participantsAdapter.updateSendingVideoStatus(personId, membership.isSendingVideo());
                Person person = mIdPersonMap.get(personId);
                auxStreamViewHolder.viewAvatar.setVisibility(membership.isSendingVideo() ? View.GONE : View.VISIBLE);
                if (person == null) {
                    auxStreamViewHolder.textView.setText(membership.getDisplayName());
                    auxStreamViewHolder.viewAvatar.setImageResource(R.drawable.google_contacts_android);
                    agent.getWebex().people().get(personId, r -> {
                        if (!r.isSuccessful() || r.getData() == null) return;
                        mIdPersonMap.put(personId, r.getData());
                        updatePersonInfoForAuxStream(personId, r.getData(), auxStreamViewHolder);
                    });
                } else {
                    auxStreamViewHolder.textView.setText(person.getDisplayName());
                    String avatar = person.getAvatar();
                    if (avatar == null || avatar.isEmpty()) {
                        auxStreamViewHolder.viewAvatar.setImageResource(R.drawable.google_contacts_android);
                    } else {
                        Picasso.with(getActivity()).cancelRequest(auxStreamViewHolder.viewAvatar);
                        Picasso.with(getActivity()).load(avatar).fit().into(auxStreamViewHolder.viewAvatar);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(OnCallMembershipEvent event) {
        CallMembership membership = event.callEvent.getCallMembership();
        Ln.d("OnCallMembershipEvent: " + membership);
        if (membership == null || membership.getPersonId() == null || membership.getPersonId().isEmpty())
            return;
        String personId = membership.getPersonId();
        if (event.callEvent instanceof CallObserver.MembershipJoinedEvent) {
            Ln.d("MembershipJoinedEvent: ");
            if (membership.getState() != CallMembership.State.JOINED || personId == null || personId.isEmpty() || membership.getDisplayName() == null || membership.getDisplayName().isEmpty())
                return;
            participantsAdapter.addOrUpdateItem(new ParticipantsAdapter.CallMembershipEntity(personId, membership.getDisplayName(), "", membership.isSendingAudio(), membership.isSendingVideo(), membership.getState()));
            agent.getWebex().people().get(personId, r -> {
                if (r == null || !r.isSuccessful() || r.getData() == null) return;
                updatePersonInfoForParticipants(personId, r.getData());
            });
        } else if (event.callEvent instanceof CallObserver.MembershipLeftEvent) {
            Ln.d("MembershipLeftEvent: ");
            participantsAdapter.removeItem(personId);
        } else if (event.callEvent instanceof CallObserver.MembershipSendingAudioEvent) {
            Ln.d("MembershipSendingAudioEvent: " + membership.isSendingAudio());
            participantsAdapter.updateSendingAudioStatus(personId, membership.isSendingAudio());
        } else if (event.callEvent instanceof CallObserver.MembershipSendingVideoEvent) {
            Ln.d("MembershipSendingVideoEvent: " + membership.isSendingVideo());
            participantsAdapter.updateSendingVideoStatus(personId, membership.isSendingVideo());
            if (participantsAdapter.getActiveSpeaker() != null && participantsAdapter.getActiveSpeaker().equals(personId)) {
                if (membership.isSendingVideo()) {
                    remoteAvatar.setVisibility(View.GONE);
                } else {
                    remoteAvatar.setVisibility(View.VISIBLE);
                    Person person = mIdPersonMap.get(personId);
                    if (person == null) {
                        remoteAvatar.setImageResource(R.drawable.google_contacts_android);
                        agent.getWebex().people().get(personId, r -> {
                            if (!r.isSuccessful() || r.getData() == null) return;
                            mIdPersonMap.put(personId, r.getData());
                            updatePersonInfoForActiveSpeaker(personId, r.getData());
                        });
                    } else {
                        String avatar = person.getAvatar();
                        if (avatar == null || avatar.isEmpty()) {
                            remoteAvatar.setImageResource(R.drawable.google_contacts_android);
                        } else {
                            Picasso.with(getActivity()).cancelRequest(remoteAvatar);
                            Picasso.with(getActivity()).load(avatar).fit().into(remoteAvatar);
                        }
                    }
                }
            }
        } else if (event.callEvent instanceof CallObserver.MembershipWaitingEvent) {
            Ln.d("MembershipJoinedLobbyEvent: ");
            if (membership.getState() != CallMembership.State.WAITING || personId == null || personId.isEmpty() || membership.getDisplayName() == null || membership.getDisplayName().isEmpty())
                return;
            participantsAdapter.addOrUpdateItem(new ParticipantsAdapter.CallMembershipEntity(personId, membership.getDisplayName(), "", membership.isSendingAudio(), membership.isSendingVideo(), membership.getState()));
            agent.getWebex().people().get(personId, r -> {
                if (r == null || !r.isSuccessful() || r.getData() == null) return;
                Ln.d("people: " + r.getData());
                updatePersonInfoForParticipants(personId, r.getData());
            });
        } else if (event.callEvent instanceof CallObserver.MembershipAudioMutedControlledEvent) {
            Ln.d("MembershipAudioMutedControlledEvent: ");
            Ln.d(membership.getPersonId() + (membership.isAudioMutedControlled() ? " muted by " : " unmuted by ") + membership.audioModifiedBy());
            if (membership.audioModifiedBy() != null) {
                String text = membership.getDisplayName() + (membership.isAudioMutedControlled() ? " muted" : " unmuted") + " by others";
                toast(text);
            }
        }
    }

    private void backToHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        this.startActivity(intent);
    }

    private void sendNotification() {
        Intent appIntent = new Intent(getActivity(), LauncherActivity.class);
        appIntent.setAction(Intent.ACTION_MAIN);
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent contentIntent = PendingIntent.getActivity(getActivity(), 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notifyManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Cisco Kichensink")
                .setContentText("I'm sharing content")
                .setContentIntent(contentIntent);
        notifyManager.notify(1, builder.build());
    }

    private void startAwakeService() {
        getActivity().startService(new Intent(getActivity(), AwakeService.class));
    }

    private void stopAwakeService() {
        getActivity().stopService(new Intent(getActivity(), AwakeService.class));
    }

    @Override
    public void onDestroy() {
        stopAwakeService();
        stopFloating();
        super.onDestroy();
    }

    public static boolean checkFloatPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            try {
                Class cls = Class.forName("android.content.Context");
                Field declaredField = cls.getDeclaredField("APP_OPS_SERVICE");
                declaredField.setAccessible(true);
                Object obj = declaredField.get(cls);
                if (!(obj instanceof String)) {
                    return false;
                }
                String str2 = (String) obj;
                obj = cls.getMethod("getSystemService", String.class).invoke(context, str2);
                cls = Class.forName("android.app.AppOpsManager");
                Field declaredField2 = cls.getDeclaredField("MODE_ALLOWED");
                declaredField2.setAccessible(true);
                Method checkOp = cls.getMethod("checkOp", Integer.TYPE, Integer.TYPE, String.class);
                int result = (Integer) checkOp.invoke(obj, 24, Binder.getCallingUid(), context.getPackageName());
                return result == declaredField2.getInt(cls);
            } catch (Exception e) {
                return false;
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AppOpsManager appOpsMgr = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
                if (appOpsMgr == null)
                    return false;
                int mode = appOpsMgr.checkOpNoThrow("android:system_alert_window", android.os.Process.myUid(), context
                        .getPackageName());
                return Settings.canDrawOverlays(context) || mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_IGNORED;
            } else {
                return Settings.canDrawOverlays(context);
            }
        }
    }

    private void requestSettingCanDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
            startActivityForResult(intent, 0x101);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0x101)
            if (checkFloatPermission(getActivity())) {
                startFloating();
            } else
                Toast.makeText(getActivity(), "No float window permission", Toast.LENGTH_SHORT).show();
    }
}
