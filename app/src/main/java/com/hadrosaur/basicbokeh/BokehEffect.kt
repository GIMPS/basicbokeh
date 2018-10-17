package com.hadrosaur.basicbokeh

fun DoBokeh(twoLens: TwoLensCoordinator) {
    //We need both shots to be done and both images in order to proceed
    if (!twoLens.normalShotDone || !twoLens.wideShotDone || (null == twoLens.normalImage)
        || (null == twoLens.wideImage))
        return


}
