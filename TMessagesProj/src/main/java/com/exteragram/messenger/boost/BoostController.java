/*

 This is the source code of KelirGram for Android.

 The exteraGram "boost" package (message encryption + zalgo filter) is closed-source and not
 published in the upstream repository, so these no-op stubs keep the client compiling. Every boost
 code path is gated behind BoostController.isUserBooster(), which returns false here, so the
 optional encryption cosmetics are simply disabled and all other features are unaffected.

*/

package com.exteragram.messenger.boost;

public class BoostController {

    public static boolean isUserBooster() {
        return false;
    }
}
