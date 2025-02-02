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


import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Switch;

import com.ciscowebex.androidsdk.kitchensink.R;
import com.ciscowebex.androidsdk.kitchensink.actions.WebexAgent;
import com.ciscowebex.androidsdk.kitchensink.actions.commands.RequirePermissionAction;
import com.ciscowebex.androidsdk.kitchensink.actions.commands.ToggleSpeakerAction;
import com.ciscowebex.androidsdk.kitchensink.actions.events.PermissionAcquiredEvent;
import com.ciscowebex.androidsdk.kitchensink.ui.BaseFragment;
import com.ciscowebex.androidsdk.phone.Phone;

import org.greenrobot.eventbus.Subscribe;

import java.util.Arrays;

import butterknife.BindView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import butterknife.OnItemSelected;

/**
 * Setup fragment, {@link BaseFragment} subclass.
 */
public class SetupFragment extends BaseFragment {

    final private int BANDWIDTH[] = {177000, 384000, 768000, 2500000, 4000000};

    private WebexAgent agent;

    @BindView(R.id.preview)
    View preview;

    @BindView(R.id.audioVideoCall)
    RadioButton switchAudioVideo;

    @BindView(R.id.audioCallOnly)
    RadioButton switchAudioOnly;

    @BindView(R.id.composited)
    RadioButton radioComposited;

    @BindView(R.id.multiStream)
    RadioButton radioMultiStream;

    @BindView(R.id.setupLoudSpeaker)
    Switch switchLoudSpeaker;

    @BindView(R.id.backCamera)
    RadioButton radioBackCamera;

    @BindView(R.id.frontCamera)
    RadioButton radioFrontCamera;

    @BindView(R.id.closePreview)
    RadioButton radioClosePreview;

    @BindView(R.id.spinnerBandWidth)
    Spinner maxBandwidth;

    @BindView(R.id.setupBNR)
    Switch switchBNR;

    @BindView(R.id.bnr_hp)
    RadioButton radioHP;

    @BindView(R.id.bnr_lp)
    RadioButton radioLP;

    public SetupFragment() {
        // Required empty public constructor
        setLayout(R.layout.fragment_setup);
    }

    @Override
    public void onStart() {
        super.onStart();
        agent = WebexAgent.getInstance();
        requirePermission();
    }

    private void requirePermission() {
        new RequirePermissionAction(getActivity()).execute();
    }

    private void setupWidgetStates() {
        // Setup loud speaker radio button
        switchLoudSpeaker.setEnabled(true);
        switchLoudSpeaker.setChecked(agent.getSpeakerPhoneOn());

        // Setup Call capability radio buttons
        if (agent.getCallCapability().equals(WebexAgent.CallCap.AUDIO_ONLY))
            switchAudioOnly.setChecked(true);
        else
            switchAudioVideo.setChecked(true);

        // Setup camera radio buttons
        radioFrontCamera.setEnabled(true);
        radioBackCamera.setEnabled(true);
        radioClosePreview.setEnabled(true);
        switch (agent.getDefaultCamera()) {
            case FRONT:
                radioFrontCamera.setChecked(true);
                setFrontCamera();
                break;
            case BACK:
                radioBackCamera.setChecked(true);
                setBackCamera();
                break;
            case CLOSE:
                radioClosePreview.setChecked(true);
                break;
        }

//         Setup max bandwidth
        int bw = agent.getMaxBandWidth();
        int index = Arrays.binarySearch(BANDWIDTH, bw);
        if (index == -1) {
            // default max bandwidth 2000000
            index = 4;
        }
        maxBandwidth.setSelection(index);

        // Setup Audio BNR
        switchBNR.setEnabled(true);
        switchBNR.setChecked(agent.isAudioBNREnable());
        if (agent.getAudioBNRMode() != null) {
            radioHP.setChecked(agent.getAudioBNRMode() == Phone.AudioBRNMode.HP);
            radioLP.setChecked(agent.getAudioBNRMode() == Phone.AudioBRNMode.LP);
        }

        // Setup multi-stream radio buttons
        if (agent.getVideoStreamMode() != null) {
            radioComposited.setChecked(agent.getVideoStreamMode() == Phone.VideoStreamMode.COMPOSITED);
            radioMultiStream.setChecked(agent.getVideoStreamMode() == Phone.VideoStreamMode.AUXILIARY);
        }

    }

    @OnClick({R.id.audioCallOnly, R.id.audioVideoCall})
    public void onSelectCallCapability(View v) {
        switch (v.getId()) {
            case R.id.audioCallOnly:
                agent.setCallCapability(WebexAgent.CallCap.AUDIO_ONLY);
                radioComposited.performClick();
                radioComposited.setEnabled(false);
                radioMultiStream.setEnabled(false);
                break;
            case R.id.audioVideoCall:
                agent.setCallCapability(WebexAgent.CallCap.AUDIO_VIDEO);
                radioComposited.setEnabled(true);
                radioMultiStream.setEnabled(true);
                break;
        }
    }

    @OnClick({R.id.closePreview, R.id.frontCamera, R.id.backCamera})
    public void onSelectCamera(View v) {
        switch (v.getId()) {
            case R.id.frontCamera:
                setFrontCamera();
                break;
            case R.id.backCamera:
                setBackCamera();
                break;
            case R.id.closePreview:
                closeCamera();
                break;
        }
    }

    @OnCheckedChanged(R.id.setupLoudSpeaker)
    public void onSetupLoadSpeakerChanged(Switch s) {
        new ToggleSpeakerAction(getActivity(), null, s.isChecked()).execute();
    }

    @OnCheckedChanged(R.id.setupBNR)
    public void onSetupBNRChanged(Switch s) {
        radioHP.setEnabled(s.isChecked());
        radioLP.setEnabled(s.isChecked());
        agent.enableAudioBNR(s.isChecked());
        if (s.isChecked()) {
            if (agent.getAudioBNRMode() != null) {
                radioHP.setChecked(agent.getAudioBNRMode() == Phone.AudioBRNMode.HP);
                radioLP.setChecked(agent.getAudioBNRMode() == Phone.AudioBRNMode.LP);
            }
        }
    }

    @OnCheckedChanged({R.id.bnr_hp, R.id.bnr_lp})
    public void onRadioBNRChanged(CompoundButton button, boolean isChecked) {
        if (isChecked) {
            switch (button.getId()) {
                case R.id.bnr_hp:
                    agent.setAudioBNRMode(Phone.AudioBRNMode.HP);
                    break;
                case R.id.bnr_lp:
                    agent.setAudioBNRMode(Phone.AudioBRNMode.LP);
                    break;
            }
        }
    }

    @OnCheckedChanged({R.id.composited, R.id.multiStream})
    public void onRadioVideoStreamModeChanged(CompoundButton button, boolean isChecked) {
        if (isChecked) {
            switch (button.getId()) {
                case R.id.composited:
                    agent.setVideoStreamMode(Phone.VideoStreamMode.COMPOSITED);
                    break;
                case R.id.multiStream:
                    agent.setVideoStreamMode(Phone.VideoStreamMode.AUXILIARY);
                    break;
            }
        }
    }

    private void closeCamera() {
        agent.setDefaultCamera(WebexAgent.CameraCap.CLOSE);
        agent.stopPreview();
        preview.setVisibility(View.GONE);
    }

    private void setBackCamera() {
        preview.setVisibility(View.VISIBLE);
        agent.setDefaultCamera(WebexAgent.CameraCap.BACK);
        agent.startPreview(preview);
    }

    private void setFrontCamera() {
        preview.setVisibility(View.VISIBLE);
        agent.setDefaultCamera(WebexAgent.CameraCap.FRONT);
        agent.startPreview(preview);
    }

    @OnItemSelected(R.id.spinnerBandWidth)
    public void onMaxBandWidthSpinnerSelected(Spinner spinner, int position) {
        agent.setMaxBandWidth(BANDWIDTH[position]);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onEventMainThread(PermissionAcquiredEvent event) {
        setupWidgetStates();
    }
}
