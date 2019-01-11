/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma version(1)
#pragma rs java_package_name(com.hadrosaur.basicbokeh)

rs_allocation allocationIn;
rs_allocation allocationOut;
rs_script script;

void root(const uchar4 *v_in, uchar4 *v_out, const void *usrData, uint32_t x, uint32_t y) {
    float4 f4in = rsUnpackColor8888(*v_in);
    float4 f4out;

    //Sepia values from https://www.techrepublic.com/blog/how-do-i/how-do-i-convert-images-to-grayscale-and-sepia-tone-using-c/
//    f4out.r = (f4in.r * 0.393) + (f4in.g * 0.769) + (f4in.b * 0.189);
//    f4out.g = (f4in.r * 0.349) + (f4in.g * 0.686) + (f4in.b * 0.168);
//    f4out.b = (f4in.r * 0.272) + (f4in.g * 0.534) + (f4in.b * 0.131);
    f4out.r = (f4in.r * 0.393) + (f4in.g * 0.769) + (f4in.b * 0.189);
    f4out.g = (f4in.r * 0.349) + (f4in.g * 0.686) + (f4in.b * 0.168);
    f4out.b = (f4in.r * 0.272) + (f4in.g * 0.534) + (f4in.b * 0.131);

    //clip
    if(f4out.r > 1.0) f4out.r = 1.0f;
    if(f4out.g > 1.0) f4out.g = 1.0f;
    if(f4out.b > 1.0) f4out.b = 1.0f;


    float3 sepia = {f4out.r, f4out.g, f4out.b};

    *v_out = rsPackColorTo8888(sepia);
}

void filter() {
    rsForEach(script, allocationIn, allocationOut);
}
