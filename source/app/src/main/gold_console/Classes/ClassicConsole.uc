// Retain UPakConsole's mission state while using Unreal's original HUD menus.
class ClassicConsole extends UPakConsole;

event bool KeyEvent(EInputKey Key, EInputAction Action, float Delta)
{
    local TransitionNullHUD Transition;
    Transition = TransitionNullHUD(Viewport.Actor.myHUD);
    // UPak's mouse-only mission confirmation also accepts gamepad A / Enter.
    if (Action == IST_Press && Transition != None
        && (Key == IK_LeftMouse || Key == IK_Enter || Key == IK_Space
            || Key == IK_Joy1 || Key == IK_Joy13))
    {
        // Native controller and joystick copies may arrive before level travel.
        // Consume them all, but request the next map only once.
        if (bTransition)
        {
            bTransition = false;
            Viewport.Actor.bFire = 0;
            Viewport.Actor.bAltFire = 0;
            Transition.EndLevel();
        }
        return true;
    }
    if (Action == IST_Press && Key == IK_LeftMouse
        && Viewport.Actor.Level.Game.IsA('CSMovie'))
        return Super.KeyEvent(Key, Action, Delta);
    return Super(Console).KeyEvent(Key, Action, Delta);
}

state Menuing
{
    function bool KeyEvent(EInputKey Key, EInputAction Action, float Delta)
    {
        if (TransitionNullHUD(Viewport.Actor.myHUD) != None)
            return Global.KeyEvent(Key, Action, Delta);
        return Super.KeyEvent(Key, Action, Delta);
    }
}

function LaunchUWindow()
{
    Viewport.Actor.ShowMenu();
}

function ToggleUWindow()
{
    Viewport.Actor.ShowMenu();
}
