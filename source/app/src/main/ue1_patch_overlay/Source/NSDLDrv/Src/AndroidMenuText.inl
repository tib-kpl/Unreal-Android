// Original Unreal menu editor. JNI only queues a result; all UObject access
// stays on the engine thread. Request IDs reject callbacks from an older field.
#if defined(__ANDROID__)
static INT AndroidMenuTextRequest = 0;
static UBOOL AndroidMenuTextWanted = 0;
struct FAndroidMenuTextResult { INT Request; UBOOL Accepted; jchar Text[128]; INT Length; };

static UBOOL AndroidMenuIsTyping(UObject* Console)
{
    return Console && Console->GetMainFrame() && Console->GetMainFrame()->StateNode
        && Console->GetMainFrame()->StateNode->GetFName() == "MenuTyping";
}

extern "C" JNIEXPORT void JNICALL Java_com_ast_unreal_UnrealSDLActivity_nativeMenuTextInput(JNIEnv* Env, jclass, jint Request, jstring Text, jboolean Accepted)
{
    FAndroidMenuTextResult* Result = (FAndroidMenuTextResult*)SDL_malloc(sizeof(FAndroidMenuTextResult));
    if(!Result) return;
    Result->Request = Request;
    Result->Accepted = Accepted != 0;
    Result->Length = Text ? Min<INT>(127, Env->GetStringLength(Text)) : 0;
    if(Result->Length) Env->GetStringRegion(Text, 0, Result->Length, Result->Text);
    SDL_Event Event;
    SDL_zero(Event);
    Event.type = SDL_USEREVENT + 0x22b;
    Event.user.data1 = Result;
    if(SDL_PushEvent(&Event) <= 0) SDL_free(Result);
}

static void AndroidMenuApplyText(UObject* Console, FAndroidMenuTextResult* Result)
{
    if(Result && Result->Request == AndroidMenuTextRequest && AndroidMenuIsTyping(Console))
    {
        UProperty* Prop = FindField<UProperty>(Console->GetClass(), "TypedStr");
        if(Prop)
        {
            char* Text = (char*)Console + Prop->Offset;
            INT Length = Result->Accepted ? Min(Result->Length, Prop->GetElementSize()-1) : 0;
            for(INT I=0; I<Length; ++I) Text[I] = Result->Text[I] <= 255 ? (char)Result->Text[I] : '?';
            Text[Length] = 0;
            UProperty* Scrollback = FindField<UProperty>(Console->GetClass(), "Scrollback");
            if(Scrollback) *(INT*)((BYTE*)Console + Scrollback->Offset) = 0;
            // Use the game's original confirm/cancel handlers (name, team, IP,
            // save title, etc.), including restoration of the old value on cancel.
            struct { BYTE Key; BYTE Action; FLOAT Delta; DWORD ReturnValue; } Params;
            appMemset(&Params, 0, sizeof(Params));
            Params.Key = Result->Accepted ? IK_Enter : IK_Escape;
            Params.Action = IST_Press;
            Console->ProcessEvent(Console->FindFunctionChecked(NAME_KeyEvent), &Params);
        }
    }
    SDL_free(Result);
}

static void AndroidMenuUpdateText(UObject* Console)
{
    UBOOL Wanted = AndroidMenuIsTyping(Console);
    if(Wanted && !AndroidMenuTextWanted)
    {
        JNIEnv* Env = (JNIEnv*)SDL_AndroidGetJNIEnv();
        jclass Class = Env ? Env->FindClass("com/ast/unreal/UnrealSDLActivity") : NULL;
        if(!Class) { if(Env) Env->ExceptionClear(); return; }
        jmethodID Show = Env->GetStaticMethodID(Class, "showMenuTextInput", "(ILjava/lang/String;I)V");
        UProperty* Prop = FindField<UProperty>(Console->GetClass(), "TypedStr");
        const char* Text = Prop ? (const char*)Console + Prop->Offset : "";
        INT Limit = Prop ? Min(127, Prop->GetElementSize()-1) : 127;
        jchar Initial[128];
        INT Length = 0;
        while(Text && Text[Length] && Length < 127) { Initial[Length] = (jchar)((unsigned char)Text[Length]); ++Length; }
        jstring String = Env->NewString(Initial, Length);
        // Stop SDL's invisible DummyEdit so it cannot take focus from the editor.
        SDL_StopTextInput();
        if(Show && String) Env->CallStaticVoidMethod(Class, Show, ++AndroidMenuTextRequest, String, Limit);
        if(Env->ExceptionCheck()) { Env->ExceptionClear(); }
        else AndroidMenuTextWanted = 1;
        if(String) Env->DeleteLocalRef(String);
        Env->DeleteLocalRef(Class);
    }
    if(!Wanted) AndroidMenuTextWanted = 0;
}
#endif
