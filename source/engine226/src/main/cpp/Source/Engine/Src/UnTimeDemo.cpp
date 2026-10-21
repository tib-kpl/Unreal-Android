/*=============================================================================
	UnTimeDemo.cpp: Unreal 226 Engine.TimeDemo natives.
=============================================================================*/

#include "EnginePrivate.h"

#if defined(PLATFORM_ANDROID)
#include "UnNativeLayoutMacros.h"
void ATimeDemo::StaticConstructor()
{
	typedef ATimeDemo FLayoutClass;
	BYTE Before[sizeof(FLayoutClass)];
	UT99_LAYOUT_FIELD(FileAr,IntProperty)
	UT99_LAYOUT_FIELD(TimePassed,FloatProperty)
	UT99_LAYOUT_FIELD(TimeDilation,FloatProperty)
	UT99_LAYOUT_FIELD(StartTime,FloatProperty)
	UT99_LAYOUT_FIELD(LastSecTime,FloatProperty)
	UT99_LAYOUT_FIELD(LastCycleTime,FloatProperty)
	UT99_LAYOUT_FIELD(LastFrameTime,FloatProperty)
	UT99_LAYOUT_FIELD(SquareSum,FloatProperty)
	UT99_LAYOUT_FIELD(FrameNum,IntProperty)
	UT99_LAYOUT_FIELD(FrameLastSecond,IntProperty)
	UT99_LAYOUT_FIELD(FrameLastCycle,IntProperty)
	UT99_LAYOUT_FIELD(CycleCount,IntProperty)
	UT99_LAYOUT_FIELD(QuitAfterCycles,IntProperty)
	UT99_LAYOUT_FIELD(CycleMessage,StrProperty)
	UT99_LAYOUT_FIELD(CycleResult,StrProperty)
	UT99_LAYOUT_BOOL(bSaveToFile)
	UT99_LAYOUT_BOOL(bFirstFrame)
	UT99_LAYOUT_FIELD(LastSec,FloatProperty)
	UT99_LAYOUT_FIELD(MinFPS,FloatProperty)
	UT99_LAYOUT_FIELD(MaxFPS,FloatProperty)
	UT99_LAYOUT_FIELD(OldPoint,ObjectProperty)
	UT99_LAYOUT_FIELD(NewPoint,ObjectProperty)
	UT99_LAYOUT_FIELD(Console,ObjectProperty)
}
#undef UT99_LAYOUT_BOOL
#undef UT99_LAYOUT_FIELD
#undef UT99_LAYOUT_ALIAS
#endif

// FileAr is a script int; 64-bit builds cannot store the archive pointer in
// it, so it holds a 1-based slot in this table.
static FArchive* GTimeDemoFiles[8];

void ATimeDemo::execOpenFile( FFrame& Stack, RESULT_DECL )
{
	guard(ATimeDemo::execOpenFile);
	P_FINISH;
	if( FileAr > 0 && FileAr <= ARRAY_COUNT(GTimeDemoFiles) && GTimeDemoFiles[FileAr-1] )
		return;
	for( INT i=0; i<ARRAY_COUNT(GTimeDemoFiles); i++ )
		if( !GTimeDemoFiles[i] )
		{
			GTimeDemoFiles[i] = GFileManager->CreateFileWriter( TEXT("timedemo.txt"), FILEWRITE_EvenIfReadOnly );
			FileAr = GTimeDemoFiles[i] ? i+1 : 0;
			break;
		}
	unguardexec;
}

void ATimeDemo::execWriteToFile( FFrame& Stack, RESULT_DECL )
{
	guard(ATimeDemo::execWriteToFile);
	P_GET_STR(Text);
	P_FINISH;
	if( FileAr > 0 && FileAr <= ARRAY_COUNT(GTimeDemoFiles) && GTimeDemoFiles[FileAr-1] )
	{
		Text += TEXT("\r\n");
		GTimeDemoFiles[FileAr-1]->Serialize( const_cast<TCHAR*>(*Text), Text.Len() );
	}
	unguardexec;
}

void ATimeDemo::execCloseFile( FFrame& Stack, RESULT_DECL )
{
	guard(ATimeDemo::execCloseFile);
	P_FINISH;
	if( FileAr > 0 && FileAr <= ARRAY_COUNT(GTimeDemoFiles) && GTimeDemoFiles[FileAr-1] )
	{
		delete GTimeDemoFiles[FileAr-1];
		GTimeDemoFiles[FileAr-1] = NULL;
	}
	FileAr = 0;
	unguardexec;
}

IMPLEMENT_CLASS(ATimeDemo);
