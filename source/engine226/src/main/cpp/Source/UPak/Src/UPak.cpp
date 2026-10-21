/*=============================================================================
	UPak.cpp: natives of Return to Na Pali (UPak.u) for Unreal Gold 226.

	PathNodeIterator builds a path of NavigationPoints between two locations
	and lets scripts walk it; PawnPathNodeIterator restricts the path to the
	reach specs its Pawn fits through.
=============================================================================*/

#include "Engine.h"
#include "UnNativeLayoutMacros.h"

#pragma pack (push,4)

class APathNodeIterator : public AActor
{
public:
	class ANavigationPoint* NodePath[64];
	INT NodeCount;
	INT NodeIndex;
	INT NodeCost;
	FVector NodeStart;
	DECLARE_FUNCTION(execGetLastVisible);
	DECLARE_FUNCTION(execGetLast);
	DECLARE_FUNCTION(execGetNext);
	DECLARE_FUNCTION(execGetCurrent);
	DECLARE_FUNCTION(execGetPrevious);
	DECLARE_FUNCTION(execGetFirst);
	DECLARE_FUNCTION(execBuildPath);
	void StaticConstructor();
	DECLARE_CLASS(APathNodeIterator,AActor,0)
	NO_DEFAULT_CONSTRUCTOR(APathNodeIterator)

	virtual APawn* PathPawn() { return NULL; }
	void BuildPath( const FVector& Start, const FVector& End );
	ANavigationPoint* NodeAt( INT Index ) { return Index>=0 && Index<NodeCount ? NodePath[Index] : NULL; }
};

class APawnPathNodeIterator : public APathNodeIterator
{
public:
	class APawn* Pawn;
	DECLARE_FUNCTION(execSetPawn);
	void StaticConstructor();
	DECLARE_CLASS(APawnPathNodeIterator,APathNodeIterator,0)
	NO_DEFAULT_CONSTRUCTOR(APawnPathNodeIterator)

	APawn* PathPawn() { return Pawn; }
};

#pragma pack (pop)

IMPLEMENT_PACKAGE(UPak);
IMPLEMENT_CLASS(APathNodeIterator);
IMPLEMENT_CLASS(APawnPathNodeIterator);

void APathNodeIterator::StaticConstructor()
{
	typedef APathNodeIterator FLayoutClass;
	UT99_LAYOUT_FIELD(NodePath,ObjectProperty)
	UT99_LAYOUT_FIELD(NodeCount,IntProperty)
	UT99_LAYOUT_FIELD(NodeIndex,IntProperty)
	UT99_LAYOUT_FIELD(NodeCost,IntProperty)
	UT99_LAYOUT_FIELD(NodeStart,StructProperty)
}

void APawnPathNodeIterator::StaticConstructor()
{
	typedef APawnPathNodeIterator FLayoutClass;
	UT99_LAYOUT_FIELD(Pawn,ObjectProperty)
}

#undef UT99_LAYOUT_BOOL
#undef UT99_LAYOUT_FIELD
#undef UT99_LAYOUT_ALIAS

// Nearest navigation point with a clear line to Location.
static ANavigationPoint* NearestVisibleNode( ULevel* Level, const FVector& Location )
{
	ANavigationPoint* Best = NULL;
	FLOAT BestDist = 0.f;
	for( ANavigationPoint* Nav=Level->GetLevelInfo()->NavigationPointList; Nav; Nav=Nav->nextNavigationPoint )
	{
		FLOAT Dist = (Nav->Location - Location).SizeSquared();
		if( (!Best || Dist < BestDist) && Level->Model->FastLineCheck(Nav->Location, Location) )
		{
			Best = Nav;
			BestDist = Dist;
		}
	}
	return Best;
}

void APathNodeIterator::BuildPath( const FVector& Start, const FVector& End )
{
	NodeCount = 0;
	NodeIndex = 0;
	NodeCost = 0;
	NodeStart = Start;
	ULevel* Level = GetLevel();
	if( !Level || !Level->GetLevelInfo() )
		return;
	ANavigationPoint* From = NearestVisibleNode( Level, Start );
	ANavigationPoint* To = NearestVisibleNode( Level, End );
	if( !From || !To )
		return;

	APawn* P = PathPawn();
	INT Radius = P ? appRound(P->CollisionRadius) : 0;
	INT Height = P ? appRound(P->CollisionHeight) : 0;
	INT MoveFlags = P ? P->calcMoveFlags() : 0;

	// Dijkstra over the reach specs; visitedWeight and previousPath are the
	// per-node scratch fields that the engine's own route finding uses.
	TArray<ANavigationPoint*> Open;
	for( ANavigationPoint* Nav=Level->GetLevelInfo()->NavigationPointList; Nav; Nav=Nav->nextNavigationPoint )
	{
		Nav->visitedWeight = MAXINT;
		Nav->previousPath = NULL;
		Nav->bEndPoint = 0;
	}
	From->visitedWeight = 0;
	Open.AddItem( From );
	while( Open.Num() )
	{
		INT BestIndex = 0;
		for( INT i=1; i<Open.Num(); i++ )
			if( Open(i)->visitedWeight < Open(BestIndex)->visitedWeight )
				BestIndex = i;
		ANavigationPoint* Node = Open(BestIndex);
		Open.Remove( BestIndex );
		if( Node->bEndPoint )
			continue;
		Node->bEndPoint = 1;
		if( Node == To )
			break;
		for( INT i=0; i<16 && Node->Paths[i]>=0; i++ )
		{
			if( Node->Paths[i] >= Level->ReachSpecs.Num() )
				break;
			FReachSpec& Spec = Level->ReachSpecs(Node->Paths[i]);
			ANavigationPoint* Next = Cast<ANavigationPoint>(Spec.End);
			if( !Next || Next->bEndPoint || Spec.bPruned )
				continue;
			if( P && !Spec.supports(Radius, Height, MoveFlags) )
				continue;
			INT Weight = Node->visitedWeight + Max(1, Spec.distance) + Next->cost;
			if( Weight < Next->visitedWeight )
			{
				if( Next->visitedWeight == MAXINT )
					Open.AddItem( Next );
				Next->visitedWeight = Weight;
				Next->previousPath = Node;
			}
		}
	}
	if( To != From && !To->previousPath )
		return;

	// Walk back from the goal, then reverse into NodePath.
	ANavigationPoint* Reverse[ARRAY_COUNT(NodePath)];
	INT Count = 0;
	for( ANavigationPoint* Nav=To; Nav && Count<ARRAY_COUNT(NodePath); Nav=Cast<ANavigationPoint>(Nav->previousPath) )
	{
		Reverse[Count++] = Nav;
		if( Nav == From )
			break;
	}
	for( INT i=0; i<Count; i++ )
		NodePath[i] = Reverse[Count-1-i];
	NodeCount = Count;
	NodeCost = To->visitedWeight;
}

void APathNodeIterator::execBuildPath( FFrame& Stack, RESULT_DECL )
{
	guard(APathNodeIterator::execBuildPath);
	P_GET_VECTOR(Start);
	P_GET_VECTOR(End);
	P_FINISH;
	BuildPath( Start, End );
	unguardexec;
}

void APathNodeIterator::execGetFirst( FFrame& Stack, RESULT_DECL )
{
	guard(APathNodeIterator::execGetFirst);
	P_FINISH;
	NodeIndex = 0;
	*(ANavigationPoint**)Result = NodeAt(NodeIndex);
	unguardexec;
}

void APathNodeIterator::execGetPrevious( FFrame& Stack, RESULT_DECL )
{
	guard(APathNodeIterator::execGetPrevious);
	P_FINISH;
	if( NodeIndex >= 0 )
		NodeIndex--;
	*(ANavigationPoint**)Result = NodeAt(NodeIndex);
	unguardexec;
}

void APathNodeIterator::execGetCurrent( FFrame& Stack, RESULT_DECL )
{
	guard(APathNodeIterator::execGetCurrent);
	P_FINISH;
	*(ANavigationPoint**)Result = NodeAt(NodeIndex);
	unguardexec;
}

void APathNodeIterator::execGetNext( FFrame& Stack, RESULT_DECL )
{
	guard(APathNodeIterator::execGetNext);
	P_FINISH;
	if( NodeIndex < NodeCount )
		NodeIndex++;
	*(ANavigationPoint**)Result = NodeAt(NodeIndex);
	unguardexec;
}

void APathNodeIterator::execGetLast( FFrame& Stack, RESULT_DECL )
{
	guard(APathNodeIterator::execGetLast);
	P_FINISH;
	NodeIndex = NodeCount - 1;
	*(ANavigationPoint**)Result = NodeAt(NodeIndex);
	unguardexec;
}

// The farthest node along the path that is still visible from NodeStart.
void APathNodeIterator::execGetLastVisible( FFrame& Stack, RESULT_DECL )
{
	guard(APathNodeIterator::execGetLastVisible);
	P_FINISH;
	ULevel* Level = GetLevel();
	INT Visible = -1;
	for( INT i=0; i<NodeCount && NodePath[i]; i++ )
	{
		if( !Level || !Level->Model->FastLineCheck(NodePath[i]->Location, NodeStart) )
			break;
		Visible = i;
	}
	if( Visible >= 0 )
		NodeIndex = Visible;
	*(ANavigationPoint**)Result = Visible >= 0 ? NodePath[Visible] : NULL;
	unguardexec;
}

void APawnPathNodeIterator::execSetPawn( FFrame& Stack, RESULT_DECL )
{
	guard(APawnPathNodeIterator::execSetPawn);
	P_GET_OBJECT(APawn,NewPawn);
	P_FINISH;
	Pawn = NewPawn;
	unguardexec;
}

IMPLEMENT_FUNCTION(APathNodeIterator,-1,execGetLastVisible);
IMPLEMENT_FUNCTION(APathNodeIterator,-1,execGetLast);
IMPLEMENT_FUNCTION(APathNodeIterator,-1,execGetNext);
IMPLEMENT_FUNCTION(APathNodeIterator,-1,execGetCurrent);
IMPLEMENT_FUNCTION(APathNodeIterator,-1,execGetPrevious);
IMPLEMENT_FUNCTION(APathNodeIterator,-1,execGetFirst);
IMPLEMENT_FUNCTION(APathNodeIterator,-1,execBuildPath);
IMPLEMENT_FUNCTION(APawnPathNodeIterator,-1,execSetPawn);
