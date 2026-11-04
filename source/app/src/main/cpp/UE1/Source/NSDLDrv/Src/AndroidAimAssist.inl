// Controller camera assistance, shared policy for v200 and retail 226.
// Does not change the original game's projectile auto-aim or mouse input.
#if defined(__ANDROID__)
// Activity.onResume can precede SDL's event queue initialization.
// Publish the preference atomically so cold launches retain Off/Light/Strong.
static std::atomic<int> AndroidAimRequestedLevel(2);
static INT AndroidAimLevel = -1;
static UBOOL AndroidAimController = 0;
static FLOAT AndroidAimStick = 0.f;
static FLOAT AndroidAimSdlAxes[2] = {0.f,0.f};
static APawn* AndroidAimPrevious = NULL; // compared only against live PawnList entries
extern "C" JNIEXPORT void JNICALL Java_com_ast_unreal_UnrealSDLActivity_nativeAimAssistSettings(JNIEnv*, jclass, jint Level)
{
    AndroidAimRequestedLevel.store(Clamp<INT>(Level, 0, 3));
}

static INT AndroidAimAngle(INT Angle) { return ((Angle + 32768) & 65535) - 32768; }

static void AndroidApplyAimAssist(UNSDLViewport* V, FLOAT DeltaTime)
{
    INT Requested = AndroidAimRequestedLevel.load();
    if(AndroidAimLevel != Requested) {
        AndroidAimLevel = Requested;
        AndroidAimPrevious = NULL;
        __android_log_print(ANDROID_LOG_INFO, "AndroidAim", "aim assist level=%d", AndroidAimLevel);
    }
    APlayerPawn* Player = V->Actor;
    if(!AndroidAimLevel || !AndroidAimController || AndroidAimStick > .65f || !Player
        || Player->Health <= 0 || !Player->Weapon || Player->bBehindView || Player->ViewTarget
        || Player->bShowMenu || (Player->myHUD && Player->myHUD->MainMenu))
    { AndroidAimPrevious = NULL; return; }
    UObject* Console = (UObject*)V->Console;
    if(Console && Console->GetMainFrame() && Console->GetMainFrame()->StateNode)
    {
        FName State = Console->GetMainFrame()->StateNode->GetFName();
        if(State == "Typing" || State == "MenuTyping" || State == "KeyMenuing" || State == "Menuing")
        { AndroidAimPrevious = NULL; return; }
    }
    static const FLOAT Cone[4] = {0.f, 2.5f, 4.f, 6.f};
    static const FLOAT Response[4] = {0.f, 2.5f, 4.f, 6.f};
    static const FLOAT Speed[4] = {0.f, 12.f, 24.f, 42.f}; // degrees / second
    FVector Eye = Player->Location + FVector(0, 0, Player->EyeHeight);
    FVector Forward = Player->ViewRotation.Vector();
    APawn* Best = NULL;
    FVector BestDirection(0,0,0);
    FLOAT BestScore = -1.f;
    for(APawn* Target = Player->Level->PawnList; Target; Target = Target->nextPawn)
    {
        if(Target == Player || Target->bDeleteMe || Target->bHidden || Target->Health <= 0
            || Target->bIsPlayer || !Target->bProjTarget) continue;
        // Friendly/neutral Nali, animals and companions must not attract the view.
        if(Target->Enemy != Player && (Target->AttitudeToPlayer < 1 || Target->AttitudeToPlayer > 3)) continue;
        FVector Point = Target->Location + FVector(0, 0, Target->CollisionHeight * .35f);
        FVector Direction = Point - Eye;
        FLOAT Distance = Direction.Size();
        if(Distance < 1.f || Distance > 4096.f) continue;
        Direction *= 1.f / Distance;
        FLOAT Dot = Forward | Direction;
        UBOOL Previous = Target == AndroidAimPrevious;
        FLOAT Threshold = (FLOAT)cos((Cone[AndroidAimLevel] + (Previous ? 1.5f : 0.f)) * PI / 180.f);
        FLOAT Score = Dot + (Previous ? .003f : 0.f);
        if(Dot < Threshold || Score <= BestScore) continue;
        FCheckResult Hit(1.f);
        V->Actor->GetLevel()->SingleLineCheck(Hit, Player, Point, Eye, TRACE_AllColliding);
        if(Hit.Time < 1.f && Hit.Actor != Target) continue;
        Best = Target;
        BestScore = Score;
        BestDirection = Point - Eye;
    }
    AndroidAimPrevious = Best;
    if(!Best) return;
    FRotator Aim = BestDirection.Rotation();
    FLOAT Dt = Clamp(DeltaTime, 0.f, .05f);
    FLOAT Blend = (1.f - (FLOAT)exp(-Response[AndroidAimLevel] * Dt)) * (1.f - AndroidAimStick);
    FLOAT MaxStep = Speed[AndroidAimLevel] * (65536.f / 360.f) * Dt;
    INT Yaw = appRound(Clamp(AndroidAimAngle(Aim.Yaw - Player->ViewRotation.Yaw) * Blend, -MaxStep, MaxStep));
    INT Pitch = appRound(Clamp(AndroidAimAngle(Aim.Pitch - Player->ViewRotation.Pitch) * Blend, -MaxStep, MaxStep));
    Player->ViewRotation.Yaw = (Player->ViewRotation.Yaw + Yaw) & 65535;
    Player->ViewRotation.Pitch = (Player->ViewRotation.Pitch + Pitch) & 65535;
}
#endif
