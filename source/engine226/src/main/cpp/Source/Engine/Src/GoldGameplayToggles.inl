// Semantic L3+A / L3+Y toggles shared with the v200 behavior.
static APlayerPawn* GAndroidTogglePlayerV21 = NULL;
static INT GAndroidTogglePlayerIndexV222 = INDEX_NONE;
static UBOOL GAndroidInfiniteHealthV21 = 0;
static UBOOL GAndroidInfiniteAmmoV21 = 0;
static FName GAndroidSavedReducedDamageTypeV21;
static INT GAndroidProtectedHealthV21 = 0;

struct FAndroidAmmoSnapshotV21
{
	AActor* AmmoObject;
	INT OriginalAmount;
	INT ProtectedAmount;
};

static const INT GAndroidMaxAmmoSnapshotsV21 = 64;
static FAndroidAmmoSnapshotV21 GAndroidAmmoSnapshotsV21[GAndroidMaxAmmoSnapshotsV21];
static INT GAndroidAmmoSnapshotCountV21 = 0;

static UBOOL UE1AndroidReadAmmoAmountV21( AActor* Ammo, INT& OutAmount, UProperty*& OutProperty )
{
	OutProperty = NULL;
	if( !Ammo || Ammo->bDeleteMe )
		return 0;

	UProperty* Property = FindField<UProperty>( Ammo->GetClass(), TEXT("AmmoAmount") );
	if( !Property )
		return 0;

	BYTE* Address = (BYTE*)Ammo + Property->Offset;
	switch( Property->ElementSize )
	{
		case 1: OutAmount = *(BYTE*)Address; break;
		case 2: OutAmount = *(SWORD*)Address; break;
		case 4: OutAmount = *(INT*)Address; break;
		default: return 0;
	}
	OutProperty = Property;
	return 1;
}

static void UE1AndroidWriteAmmoAmountV21( AActor* Ammo, UProperty* Property, INT Amount )
{
	if( !Ammo || Ammo->bDeleteMe || !Property )
		return;

	BYTE* Address = (BYTE*)Ammo + Property->Offset;
	switch( Property->ElementSize )
	{
		case 1: *(BYTE*)Address = (BYTE)Clamp( Amount, 0, 255 ); break;
		case 2: *(SWORD*)Address = (SWORD)Clamp( Amount, -32768, 32767 ); break;
		case 4: *(INT*)Address = Amount; break;
	}
}

static APlayerPawn* UE1AndroidLiveTogglePlayerV222()
{
	// UNREAL_ANDROID_INFINITE_TOGGLES_LIFETIME_V222
	// A map change or savegame load garbage-collects the old pawn before
	// TickInput attaches the new one, so the level tick in between must not
	// dereference the stored pointer. The object table entry is cleared when
	// the object is destroyed; only trust the pointer while it is still there.
	if( !GAndroidTogglePlayerV21 || GAndroidTogglePlayerIndexV222 == INDEX_NONE )
		return NULL;
	if( UObject::GetIndexedObject( GAndroidTogglePlayerIndexV222 ) != (UObject*)GAndroidTogglePlayerV21 )
		return NULL;
	return GAndroidTogglePlayerV21;
}

static UBOOL UE1AndroidActorOwnedByTogglePlayerV21( AActor* Actor, APlayerPawn* Player )
{
	INT OwnerDepth = 0;
	for( AActor* Test=Actor; Test && OwnerDepth<8; Test=Test->Owner, ++OwnerDepth )
	{
		if( Test == Player )
			return 1;
	}
	return 0;
}

static INT UE1AndroidFindAmmoSnapshotV21( AActor* Ammo )
{
	if( !Ammo )
		return INDEX_NONE;

	for( INT i=0; i<GAndroidAmmoSnapshotCountV21; ++i )
	{
		if( GAndroidAmmoSnapshotsV21[i].AmmoObject == Ammo )
			return i;
	}
	return INDEX_NONE;
}

static void UE1AndroidCaptureCurrentAmmoV21( APlayerPawn* Player )
{
	if( !Player )
		return;

	INT GuardCount = 0;
	for( AInventory* Item=Player->Inventory; Item && GuardCount<256; Item=Item->Inventory, ++GuardCount )
	{
		AActor* Ammo = (AActor*)Item;
		INT Amount = 0;
		UProperty* Property = NULL;
		if( !UE1AndroidReadAmmoAmountV21( Ammo, Amount, Property ) )
			continue;
		if( UE1AndroidFindAmmoSnapshotV21( Ammo ) != INDEX_NONE )
			continue;
		if( GAndroidAmmoSnapshotCountV21 >= GAndroidMaxAmmoSnapshotsV21 )
			break;

		FAndroidAmmoSnapshotV21& Snapshot = GAndroidAmmoSnapshotsV21[GAndroidAmmoSnapshotCountV21++];
		Snapshot.AmmoObject = Ammo;
		Snapshot.OriginalAmount = Amount;
		Snapshot.ProtectedAmount = Amount;
	}
}


static void UE1AndroidProtectCurrentAmmoV23( APlayerPawn* Player )
{
	if( !Player )
		return;

	INT GuardCount = 0;
	for( AInventory* Item=Player->Inventory; Item && GuardCount<256; Item=Item->Inventory, ++GuardCount )
	{
		AActor* Ammo = (AActor*)Item;
		INT Amount = 0;
		UProperty* Property = NULL;
		if( !UE1AndroidReadAmmoAmountV21( Ammo, Amount, Property ) )
			continue;

		INT SnapshotIndex = UE1AndroidFindAmmoSnapshotV21( Ammo );
		if( SnapshotIndex == INDEX_NONE )
		{
			if( GAndroidAmmoSnapshotCountV21 >= GAndroidMaxAmmoSnapshotsV21 )
				continue;

			SnapshotIndex = GAndroidAmmoSnapshotCountV21++;
			FAndroidAmmoSnapshotV21& NewSnapshot = GAndroidAmmoSnapshotsV21[SnapshotIndex];
			NewSnapshot.AmmoObject = Ammo;
			NewSnapshot.OriginalAmount = Amount;
			NewSnapshot.ProtectedAmount = Amount;
		}

		FAndroidAmmoSnapshotV21& Snapshot = GAndroidAmmoSnapshotsV21[SnapshotIndex];
		if( Amount < Snapshot.ProtectedAmount )
		{
			UE1AndroidWriteAmmoAmountV21( Ammo, Property, Snapshot.ProtectedAmount );
		}
		else if( Amount > Snapshot.ProtectedAmount )
		{
			// Pickups may still increase ammo while the toggle is enabled.
			Snapshot.ProtectedAmount = Amount;
		}
	}
}

static void UE1AndroidRestoreAmmoSnapshotsV21( APlayerPawn* Player )
{
	if( Player )
	{
		INT GuardCount = 0;
		for( AInventory* Item=Player->Inventory; Item && GuardCount<256; Item=Item->Inventory, ++GuardCount )
		{
			AActor* Ammo = (AActor*)Item;
			const INT SnapshotIndex = UE1AndroidFindAmmoSnapshotV21( Ammo );
			if( SnapshotIndex == INDEX_NONE )
				continue;

			INT CurrentAmount = 0;
			UProperty* Property = NULL;
			if( UE1AndroidReadAmmoAmountV21( Ammo, CurrentAmount, Property ) )
				UE1AndroidWriteAmmoAmountV21( Ammo, Property, GAndroidAmmoSnapshotsV21[SnapshotIndex].OriginalAmount );
		}
	}

	GAndroidAmmoSnapshotCountV21 = 0;
	appMemset( GAndroidAmmoSnapshotsV21, 0, sizeof(GAndroidAmmoSnapshotsV21) );
}

static void UE1AndroidAttachTogglePlayerV21( APlayerPawn* Player )
{
	// A new pawn can reuse the freed pawn's address, so also compare the object index.
	if( Player == GAndroidTogglePlayerV21 && ( !Player || (INT)Player->GetIndex() == GAndroidTogglePlayerIndexV222 ) )
		return;

	// A level/load transition replaces the pawn.  Never dereference the old
	// pointer here; simply start the optional toggles disabled for the new pawn.
	GAndroidTogglePlayerV21 = Player;
	GAndroidTogglePlayerIndexV222 = Player ? (INT)Player->GetIndex() : INDEX_NONE;
	GAndroidInfiniteHealthV21 = 0;
	GAndroidProtectedHealthV21 = 0;
	GAndroidInfiniteAmmoV21 = 0;
	GAndroidAmmoSnapshotCountV21 = 0;
	appMemset( GAndroidAmmoSnapshotsV21, 0, sizeof(GAndroidAmmoSnapshotsV21) );
}

extern "C" ENGINE_API UBOOL UE1AndroidToggleInfiniteHealthV21( APlayerPawn* Player )
{
	UE1AndroidAttachTogglePlayerV21( Player );
	if( !Player )
		return 0;

	if( !GAndroidInfiniteHealthV21 )
	{
		GAndroidSavedReducedDamageTypeV21 = Player->ReducedDamageType;
		GAndroidProtectedHealthV21 = Player->Health;
		GAndroidInfiniteHealthV21 = 1;
		Player->ReducedDamageType = FName(TEXT("All"));
	}
	else
	{
		GAndroidInfiniteHealthV21 = 0;
		GAndroidProtectedHealthV21 = 0;
		Player->ReducedDamageType = GAndroidSavedReducedDamageTypeV21;
	}
	return GAndroidInfiniteHealthV21;
}

extern "C" ENGINE_API UBOOL UE1AndroidToggleInfiniteAmmoV21( APlayerPawn* Player )
{
	UE1AndroidAttachTogglePlayerV21( Player );
	if( !Player )
		return 0;

	if( !GAndroidInfiniteAmmoV21 )
	{
		GAndroidAmmoSnapshotCountV21 = 0;
		appMemset( GAndroidAmmoSnapshotsV21, 0, sizeof(GAndroidAmmoSnapshotsV21) );
		UE1AndroidCaptureCurrentAmmoV21( Player );
		GAndroidInfiniteAmmoV21 = 1;
	}
	else
	{
		GAndroidInfiniteAmmoV21 = 0;
		UE1AndroidRestoreAmmoSnapshotsV21( Player );
	}
	return GAndroidInfiniteAmmoV21;
}

extern "C" ENGINE_API void UE1AndroidUpdateInfiniteTogglesV21( APlayerPawn* Player )
{
	UE1AndroidAttachTogglePlayerV21( Player );
	if( !Player )
		return;

	if( GAndroidInfiniteHealthV21 )
	{
		Player->ReducedDamageType = FName(TEXT("All"));
		// Standard damage is blocked by ReducedDamageType.  The monotonic floor
		// also catches scripted/direct Health subtraction while still allowing
		// medkits and other increases to raise the protected value.
		if( Player->Health < GAndroidProtectedHealthV21 )
			Player->Health = GAndroidProtectedHealthV21;
		else if( Player->Health > GAndroidProtectedHealthV21 )
			GAndroidProtectedHealthV21 = Player->Health;
	}
	if( GAndroidInfiniteAmmoV21 )
		UE1AndroidProtectCurrentAmmoV23( Player );
}

extern "C" ENGINE_API void UE1AndroidRefreshInfiniteAmmoAfterLevelTickV23( ULevel* Level )
{
	if( !GAndroidInfiniteAmmoV21 || !Level )
		return;
	APlayerPawn* Player = UE1AndroidLiveTogglePlayerV222(); // UNREAL_ANDROID_INFINITE_TOGGLES_LIFETIME_V222
	if( !Player || Player->bDeleteMe || Player->XLevel != Level )
		return;

	UE1AndroidProtectCurrentAmmoV23( Player );
}

extern "C" ENGINE_API void UE1AndroidSuspendTogglesForSaveV222( UBOOL bSuspend )
{
	// UNREAL_ANDROID_INFINITE_TOGGLES_LIFETIME_V222
	// ReducedDamageType is an ordinary saved property. Write the player's real
	// value into the savegame: after loading, the toggle always starts OFF, so a
	// saved 'All' would otherwise leave the player invulnerable for good.
	if( !GAndroidInfiniteHealthV21 )
		return;
	APlayerPawn* Player = UE1AndroidLiveTogglePlayerV222();
	if( !Player )
		return;
	Player->ReducedDamageType = bSuspend ? GAndroidSavedReducedDamageTypeV21 : FName(TEXT("All"));
}

// Keep the older ProcessEvent guard as an immediate secondary safety net for
// native-to-script UseAmmo calls. Script-to-script UseAmmo calls are covered by
// the post-level-tick freeze above.
static UBOOL UE1AndroidShouldPreserveAmmoUseV21( AActor* Actor, UFunction* Function, INT& Before, UProperty*& Property )
{
	if( !GAndroidInfiniteAmmoV21 || !GAndroidTogglePlayerV21 || !Actor || !Function )
		return 0;

	static FName UseAmmoNameV21( TEXT("UseAmmo") );
	if( Function->GetFName() != UseAmmoNameV21 )
		return 0;
	APlayerPawn* Player = UE1AndroidLiveTogglePlayerV222(); // UNREAL_ANDROID_INFINITE_TOGGLES_LIFETIME_V222
	if( !Player || !UE1AndroidActorOwnedByTogglePlayerV21( Actor, Player ) )
		return 0;

	return UE1AndroidReadAmmoAmountV21( Actor, Before, Property );
}
