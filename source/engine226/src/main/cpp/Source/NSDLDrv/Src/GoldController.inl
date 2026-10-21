// Gold uses v200 Android key/axis normalization for built-in, USB and Bluetooth
// controllers. SDL is retained as a fallback; duplicate raw JOY events are dropped.
static UBOOL GoldPadKeys[IK_MAX] = {0};
static INT GoldPadButtons[SDL_CONTROLLER_BUTTON_MAX] = {0};
static FLOAT GoldPadAxes[SDL_CONTROLLER_AXIS_MAX] = {0};
static FLOAT GoldPadFiltered[2] = {0};
static UBOOL GoldPadActive[2] = {0};
static INT GoldPadMenu = -1;
static UBOOL GoldPadDirectAndroid=0;
static UBOOL GoldPadDigitalTriggers[2]={0};
static UBOOL GoldPadCaptureArmed=0;
static UBOOL GoldPadMenuArmed=0;
static UBOOL GoldPadMenuAxes[4]={0};

static UBOOL GoldPadInMenu( UNSDLViewport* V )
{
    if( V->bShowWindowsMouse || (V->Actor && V->Actor->myHUD && V->Actor->myHUD->MainMenu) ) return 1;
    if( V->Console && ((UObject*)V->Console)->GetStateFrame() && ((UObject*)V->Console)->GetStateFrame()->StateNode )
    {
        FName State = ((UObject*)V->Console)->GetStateFrame()->StateNode->GetFName();
        return State == "Menuing" || State == "KeyMenuing" || State == "MenuTyping" || State == "Typing";
    }
    return 0;
}

extern "C" UBOOL UE1AndroidToggleInfiniteHealthV21(APlayerPawn* Player);
extern "C" UBOOL UE1AndroidToggleInfiniteAmmoV21(APlayerPawn* Player);
extern "C" void UE1AndroidUpdateInfiniteTogglesV21(APlayerPawn* Player);
static UBOOL GAndroidToggleL3HeldV21=0;
static UBOOL GAndroidToggleFaceDownV21[2]={0};
static UBOOL GAndroidToggleFaceLatchedV21[2]={0};
static UBOOL GAndroidToggleFaceConsumedV21[2]={0};
enum EAndroidToggleFaceV21
{
	ATF_AmmoV21 = 0,
	ATF_HealthV21,
	ATF_CountV21
};

static INT UE1AndroidToggleFaceFromUE1KeyV21( INT Key )
{
	switch( Key )
	{
		case IK_Joy1: return ATF_AmmoV21;
		case IK_Joy4: return ATF_HealthV21;
	}
	return INDEX_NONE;
}

static UBOOL UE1AndroidToggleInputBlockedV21( UNSDLViewport* Viewport )
{
	if( !Viewport || !Viewport->Actor )
		return 1;

	if( GoldPadInMenu(Viewport) ) return 1;
	if( Viewport->Console )
	{
		UObject* ConsoleObject = (UObject*)Viewport->Console;
		if( ConsoleObject->GetStateFrame() && ConsoleObject->GetStateFrame()->StateNode )
		{
			const FName StateName = ConsoleObject->GetStateFrame()->StateNode->GetFName();
			if( StateName == "Menuing" || StateName == "KeyMenuing" || StateName == "MenuTyping" ||
				StateName == "Typing" || StateName == "Console" )
				return 1;
		}
	}
	return 0;
}

static void UE1AndroidRunSemanticToggleV21( UNSDLViewport* Viewport, INT Face )
{
	if( !Viewport || !Viewport->Actor )
		return;

	UBOOL Enabled = 0;
	const char* Message = NULL;
	if( Face == ATF_AmmoV21 )
	{
		Enabled = UE1AndroidToggleInfiniteAmmoV21( Viewport->Actor );
		Message = Enabled ? "Infinite Ammo: ON" : "Infinite Ammo: OFF";
	}
	else if( Face == ATF_HealthV21 )
	{
		Enabled = UE1AndroidToggleInfiniteHealthV21( Viewport->Actor );
		Message = Enabled ? "Infinite Health: ON" : "Infinite Health: OFF";
	}

	if( Message )
	{
		Viewport->Actor->eventClientMessage( FString(Message), NAME_None, 0 );
		debugf( NAME_Log, "Android semantic controller toggle L3: %s", Message );
	}
}

static void UE1AndroidResetToggleComboInputV21()
{
	GAndroidToggleL3HeldV21 = 0;
	appMemset( GAndroidToggleFaceDownV21, 0, sizeof(GAndroidToggleFaceDownV21) );
	appMemset( GAndroidToggleFaceLatchedV21, 0, sizeof(GAndroidToggleFaceLatchedV21) );
	appMemset( GAndroidToggleFaceConsumedV21, 0, sizeof(GAndroidToggleFaceConsumedV21) );
}

static UBOOL UE1AndroidHandleSemanticToggleComboV21( UNSDLViewport* Viewport, INT Key, EInputAction Action )
{
	if( Action != IST_Press && Action != IST_Release )
		return 0;

	if( UE1AndroidToggleInputBlockedV21( Viewport ) )
	{
		UE1AndroidResetToggleComboInputV21();
		return 0;
	}

	const UBOOL bPressed = Action == IST_Press;
	if( Key == IK_Joy8 )
	{
		GAndroidToggleL3HeldV21 = bPressed;
		if( bPressed )
		{
			// Also supports the less common event order where the face button
			// reaches UE1 one event before L3 in the same physical chord.
			for( INT Face=0; Face<ATF_CountV21; ++Face )
			{
				if( GAndroidToggleFaceDownV21[Face] && !GAndroidToggleFaceLatchedV21[Face] )
				{
					GAndroidToggleFaceLatchedV21[Face] = 1;
					GAndroidToggleFaceConsumedV21[Face] = 1;
					UE1AndroidRunSemanticToggleV21( Viewport, Face );
				}
			}
		}
		else
		{
			appMemset( GAndroidToggleFaceLatchedV21, 0, sizeof(GAndroidToggleFaceLatchedV21) );
		}

		// L3 is reserved as the toggle modifier and must not invoke a normal bind.
		return 1;
	}

	const INT Face = UE1AndroidToggleFaceFromUE1KeyV21( Key );
	if( Face == INDEX_NONE )
		return 0;

	if( !bPressed )
	{
		GAndroidToggleFaceDownV21[Face] = 0;
		GAndroidToggleFaceLatchedV21[Face] = 0;
		if( GAndroidToggleFaceConsumedV21[Face] )
		{
			GAndroidToggleFaceConsumedV21[Face] = 0;
			return 1;
		}
		return 0;
	}

	GAndroidToggleFaceDownV21[Face] = 1;
	if( GAndroidToggleL3HeldV21 && !GAndroidToggleFaceLatchedV21[Face] )
	{
		GAndroidToggleFaceLatchedV21[Face] = 1;
		GAndroidToggleFaceConsumedV21[Face] = 1;
		UE1AndroidRunSemanticToggleV21( Viewport, Face );
		return 1;
	}

	return 0;
}


static void GoldPadKey( UNSDLViewport* V, INT Key, UBOOL Down )
{
    if( Key <= IK_None || Key >= IK_MAX || GoldPadKeys[Key] == Down ) return;
    GoldPadKeys[Key] = Down;
    if( !UE1AndroidHandleSemanticToggleComboV21(V,Key,Down ? IST_Press : IST_Release) )
        V->CauseInputEvent( Key, Down ? IST_Press : IST_Release );
    UT99_ANDROID_SDL_LOGI("Gold controller key=%d %s", Key, Down ? "down" : "up");
}

static void GoldPadReset( UNSDLViewport* V )
{
    for( INT Key=1; Key<IK_MAX; ++Key ) GoldPadKey(V, Key, 0);
    UE1AndroidResetToggleComboInputV21();
    UT99V47ReleaseGameplayStatesV79(V);
    GUT99V47LX=GUT99V47LY=GUT99V47RX=GUT99V47RY=0.0f;
    GUT99V50LX=GUT99V50LY=GUT99V50RX=GUT99V50RY=0.0f;
    appMemset(GoldPadMenuAxes,0,sizeof(GoldPadMenuAxes));
    GoldPadMenuArmed=0;
    appMemset(GoldPadButtons, 0, sizeof(GoldPadButtons));
    appMemset(GoldPadDigitalTriggers,0,sizeof(GoldPadDigitalTriggers));
    appMemset(GoldPadAxes, 0, sizeof(GoldPadAxes));
    appMemset(GoldPadFiltered, 0, sizeof(GoldPadFiltered));
    appMemset(GoldPadActive, 0, sizeof(GoldPadActive));
}

static UBOOL GoldPadCapturing( UNSDLViewport* V )
{
    UObject* Console=(UObject*)V->Console;
    return Console && Console->GetStateFrame() && Console->GetStateFrame()->StateNode
        && Console->GetStateFrame()->StateNode->GetFName()=="KeyMenuing";
}

static void GoldPadSyncMenu( UNSDLViewport* V )
{
    INT Menu = GoldPadCapturing(V) ? 2 : GoldPadInMenu(V) ? 1 : 0;
    if( Menu != GoldPadMenu )
    {
        FLOAT Axes[SDL_CONTROLLER_AXIS_MAX];
        appMemcpy(Axes,GoldPadAxes,sizeof(Axes));
        GoldPadReset(V);
        if( Menu!=0 ) appMemcpy(GoldPadAxes,Axes,sizeof(Axes));
        GoldPadCaptureArmed=0;
        GoldPadMenu = Menu;
    }
}

static INT GoldPadArrowIndex(INT Key)
{
    switch(Key) { case IK_Left:return 0; case IK_Right:return 1; case IK_Up:return 2; case IK_Down:return 3; }
    return -1;
}

static void GoldPadButton( UNSDLViewport* V, INT Button, UBOOL Down )
{
    GoldPadSyncMenu(V);
    if( Button < 0 || Button >= SDL_CONTROLLER_BUTTON_MAX ) return;
    // Release the key selected at press time, even if a menu opened meanwhile.
    if( !Down )
    {
        const INT Key=GoldPadButtons[Button];
        const INT Arrow=GoldPadArrowIndex(Key);
        GoldPadButtons[Button] = IK_None;
        // A D-pad release must not release a still-held stick direction.
        if( Arrow<0 || !GoldPadMenuAxes[Arrow] ) GoldPadKey(V,Key,0);
        return;
    }
    if( GoldPadButtons[Button] ) return;
    const UBOOL UI = GoldPadMenu == 1;
    INT Key = IK_None;
    switch( Button )
    {
        case SDL_CONTROLLER_BUTTON_A: Key=UI ? IK_Enter : IK_Joy1; break;
        case SDL_CONTROLLER_BUTTON_B: Key=UI ? IK_Escape : IK_Joy2; break;
        case SDL_CONTROLLER_BUTTON_X: Key=UI ? IK_N : IK_Joy3; break;
        case SDL_CONTROLLER_BUTTON_Y: Key=UI ? IK_Y : IK_Joy4; break;
        case SDL_CONTROLLER_BUTTON_BACK: Key=UI ? IK_Escape : IK_Joy5; break;
        case SDL_CONTROLLER_BUTTON_LEFTSTICK: Key=IK_Joy8; break;
        case SDL_CONTROLLER_BUTTON_RIGHTSTICK: Key=IK_Joy9; break;
        case SDL_CONTROLLER_BUTTON_LEFTSHOULDER: Key=IK_Joy10; break;
        case SDL_CONTROLLER_BUTTON_RIGHTSHOULDER: Key=IK_Joy11; break;
        case SDL_CONTROLLER_BUTTON_DPAD_UP: Key=UI ? IK_Up : IK_JoyPovUp; break;
        case SDL_CONTROLLER_BUTTON_DPAD_DOWN: Key=UI ? IK_Down : IK_JoyPovDown; break;
        case SDL_CONTROLLER_BUTTON_DPAD_LEFT: Key=UI ? IK_Left : IK_JoyPovLeft; break;
        case SDL_CONTROLLER_BUTTON_DPAD_RIGHT: Key=UI ? IK_Right : IK_JoyPovRight; break;
        case SDL_CONTROLLER_BUTTON_START:
        case SDL_CONTROLLER_BUTTON_GUIDE:
            // Same single Start toggle used by the touch overlay.
            UT99V72QueueStartToggle("gold-controller");
            GoldPadButtons[Button] = IK_Escape;
            return;
    }
    GoldPadButtons[Button] = Key;
    GoldPadKey(V, Key, 1);
}

static FLOAT GoldPadDeadzone( FLOAT Value, FLOAT Dead )
{
    Dead=Clamp(Dead, 0.0f, 0.85f);
    if( Abs(Value) <= Dead ) return 0.0f;
    // v200 uses a linear response after renormalizing the deadzone.
    FLOAT Result=Clamp((Abs(Value)-Dead)/(1.0f-Dead),0.0f,1.0f);
    return Value < 0 ? -Result : Result;
}

static FLOAT GoldPadSmooth( INT Axis, FLOAT Target )
{
    // Same engage/release hysteresis and adaptive smoothing as v200.
    if( !GoldPadActive[Axis] )
    {
        if( Abs(Target)<0.035f ) return GoldPadFiltered[Axis]=0;
        GoldPadActive[Axis]=1;
        return GoldPadFiltered[Axis]=Target;
    }
    if( Abs(Target)<0.018f )
    {
        GoldPadActive[Axis]=0;
        return GoldPadFiltered[Axis]=0;
    }
    FLOAT& Filtered=GoldPadFiltered[Axis];
    if( (Filtered<0)!=(Target<0) ) Filtered=Target;
    else Filtered+=(Target-Filtered)*(Abs(Target-Filtered)>0.30f ? 0.68f : 0.34f);
    return Filtered;
}

static UBOOL GoldPadLookBinding( UNSDLViewport* V, INT Key )
{
    if( !V->Input ) return 1;
    const TCHAR* Bind=*V->Input->Bindings[Key];
    return !*Bind || appStricmp(Bind,TEXT("TurnLeft"))==0 || appStricmp(Bind,TEXT("TurnRight"))==0
        || appStricmp(Bind,TEXT("LookUp"))==0 || appStricmp(Bind,TEXT("LookDown"))==0;
}

static void GoldPadDirection( UNSDLViewport* V, INT Key, FLOAT Value, UBOOL UI, UBOOL Right=0 )
{
    const FLOAT Press=UI ? 0.50f : Right ? 0.25f : 0.35f;
    const FLOAT Release=Right ? 0.125f : 0.25f;
    GoldPadKey(V,Key,Value>=(GoldPadKeys[Key] ? Release : Press));
}

static void GoldPadTick( UNSDLViewport* V )
{
    GoldPadSyncMenu(V);
    UE1AndroidUpdateInfiniteTogglesV21(V->Actor);
    if( V->bShowWindowsMouse ) return; // UWindow retains its pointer/capture controls.
    const UBOOL UI=GoldPadMenu==1;
    const UBOOL Capture=GoldPadMenu==2;
    if( Capture && !GoldPadCaptureArmed )
    {
        UBOOL Neutral=1;
        for(INT Axis=0;Axis<SDL_CONTROLLER_AXIS_MAX;++Axis)
            if( Abs(GoldPadAxes[Axis])>0.25f ) Neutral=0;
        GoldPadCaptureArmed=Neutral;
        if( !Neutral ) return;
    }
    FLOAT LeftDead=0.08f, RightDead=0.10f, TriggerDead=0.12f, Scale=1.0f;
    GConfig->GetFloat(TEXT("NSDLDrv.NSDLClient"),TEXT("AndroidNativeLeftStickDeadzone"),LeftDead);
    GConfig->GetFloat(TEXT("NSDLDrv.NSDLClient"),TEXT("AndroidNativeRightStickDeadzone"),RightDead);
    GConfig->GetFloat(TEXT("NSDLDrv.NSDLClient"),TEXT("AndroidNativeTriggerDeadzone"),TriggerDead);
    GConfig->GetFloat(TEXT("NSDLDrv.NSDLClient"),TEXT("AndroidNativeRightStickScale"),Scale);
    const FLOAT LX=GoldPadDeadzone(GoldPadAxes[0],LeftDead), LY=GoldPadDeadzone(GoldPadAxes[1],LeftDead);
    if( UI )
    {
        // Opening a menu while moving must not navigate it until the stick
        // returns to centre. Track stick and D-pad separately so one source
        // cannot release/re-trigger the other's held arrow each frame.
        if( !GoldPadMenuArmed && Abs(LX)<=0.25f && Abs(LY)<=0.25f ) GoldPadMenuArmed=1;
        const FLOAT Direction[4]={-LX,LX,-LY,LY};
        const INT Keys[4]={IK_Left,IK_Right,IK_Up,IK_Down};
        const INT Buttons[4]={SDL_CONTROLLER_BUTTON_DPAD_LEFT,SDL_CONTROLLER_BUTTON_DPAD_RIGHT,
            SDL_CONTROLLER_BUTTON_DPAD_UP,SDL_CONTROLLER_BUTTON_DPAD_DOWN};
        for(INT i=0;i<4;++i)
        {
            GoldPadMenuAxes[i]=GoldPadMenuArmed && Direction[i]>=(GoldPadMenuAxes[i] ? 0.25f : 0.50f);
            GoldPadKey(V,Keys[i],GoldPadMenuAxes[i] || GoldPadButtons[Buttons[i]]!=IK_None);
        }
        return;
    }
    GoldPadDirection(V,IK_UnknownD8,-LX,0,Capture);
    GoldPadDirection(V,IK_UnknownD9,LX,0,Capture);
    GoldPadDirection(V,IK_UnknownDA,-LY,0,Capture);
    GoldPadDirection(V,IK_UnknownDF,LY,0,Capture);
    GoldPadKey(V,IK_Joy12,GoldPadDeadzone(Max(GoldPadAxes[4],GoldPadDigitalTriggers[0] ? 1.0f : 0.0f),TriggerDead)>(GoldPadKeys[IK_Joy12] ? 0.125f : 0.25f));
    GoldPadKey(V,IK_Joy13,GoldPadDeadzone(Max(GoldPadAxes[5],GoldPadDigitalTriggers[1] ? 1.0f : 0.0f),TriggerDead)>(GoldPadKeys[IK_Joy13] ? 0.125f : 0.25f));
    const INT Negative[2]={IK_Joy14,IK_Joy16}, Positive[2]={IK_Joy15,IK_UnknownEA};
    if( Capture )
    {
        for(INT Axis=0;Axis<2;++Axis)
        {
            FLOAT Value=GoldPadDeadzone(GoldPadAxes[Axis+2],RightDead);
            GoldPadDirection(V,Negative[Axis],-Value,0,1);
            GoldPadDirection(V,Positive[Axis],Value,0,1);
        }
        return;
    }
    const FLOAT Sensitivity=V->Actor ? V->Actor->MouseSensitivity : 3.0f;
    const FLOAT Factor=Clamp(Sensitivity/4.0f,0.20f,3.0f)*Clamp(Scale,0.05f,2.0f);
    for( INT Axis=0; Axis<2; ++Axis )
    {
        FLOAT Value=GoldPadDeadzone(GoldPadAxes[Axis+2],RightDead);
        UBOOL Analog=GoldPadLookBinding(V,Value<0 ? Negative[Axis] : Positive[Axis]);
        GoldPadDirection(V,Negative[Axis],Analog ? 0 : -Value,0,1);
        GoldPadDirection(V,Positive[Axis],Analog ? 0 : Value,0,1);
        Value=GoldPadSmooth(Axis,Analog ? Value : 0);
        UBOOL Invert=0;
        GConfig->GetBool(TEXT("NSDLDrv.NSDLClient"),Axis ? TEXT("InvertV") : TEXT("InvertU"),Invert);
        FLOAT Delta=Value*(Axis ? 9.0f : 11.5f)*Factor*(Invert ? -1.0f : 1.0f);
        if( Abs(Delta)>0.0001f ) V->CauseInputEvent(Axis ? IK_MouseY : IK_MouseX,IST_Axis,Delta);
    }
}

// Queue on the Android UI thread, consume on the engine thread. No UObject
// or held-state access crosses that boundary, including pause/disconnect resets.
extern "C" JNIEXPORT void JNICALL Java_com_ast_unreal_Unreal226Activity_nativeGoldControllerButton(JNIEnv*,jclass,jint Key,jboolean Down)
{
    SDL_Event Event; SDL_memset(&Event,0,sizeof(Event));
    Event.type=SDL_USEREVENT+0x226; Event.user.code=Key;
    Event.user.data1=(void*)(PTRINT)(Down ? 1 : 0); SDL_PushEvent(&Event);
}
extern "C" JNIEXPORT void JNICALL Java_com_ast_unreal_Unreal226Activity_nativeGoldControllerAxis(JNIEnv*,jclass,jint Axis,jfloat Value)
{
    SDL_Event Event; SDL_memset(&Event,0,sizeof(Event));
    Event.type=SDL_USEREVENT+0x227; Event.user.code=Axis;
    Event.user.data1=(void*)(PTRINT)(INT)(Clamp((FLOAT)Value,-1.0f,1.0f)*32767.0f); SDL_PushEvent(&Event);
}
extern "C" JNIEXPORT void JNICALL Java_com_ast_unreal_Unreal226Activity_nativeGoldControllerReset(JNIEnv*,jclass)
{
    SDL_Event Event; SDL_memset(&Event,0,sizeof(Event));
    Event.type=SDL_USEREVENT+0x228; SDL_PushEvent(&Event);
}

static INT GoldPadAndroidButton(INT Key)
{
    switch(Key)
    {
        case 96: return SDL_CONTROLLER_BUTTON_A;
        case 97: return SDL_CONTROLLER_BUTTON_B;
        case 99: return SDL_CONTROLLER_BUTTON_X;
        case 100: return SDL_CONTROLLER_BUTTON_Y;
        case 102: return SDL_CONTROLLER_BUTTON_LEFTSHOULDER;
        case 103: return SDL_CONTROLLER_BUTTON_RIGHTSHOULDER;
        case 106: return SDL_CONTROLLER_BUTTON_LEFTSTICK;
        case 107: return SDL_CONTROLLER_BUTTON_RIGHTSTICK;
        case 109: return SDL_CONTROLLER_BUTTON_BACK;
        case 82: case 108: case 110: return SDL_CONTROLLER_BUTTON_START;
        case 19: return SDL_CONTROLLER_BUTTON_DPAD_UP;
        case 20: return SDL_CONTROLLER_BUTTON_DPAD_DOWN;
        case 21: return SDL_CONTROLLER_BUTTON_DPAD_LEFT;
        case 22: return SDL_CONTROLLER_BUTTON_DPAD_RIGHT;
    }
    return -1;
}
