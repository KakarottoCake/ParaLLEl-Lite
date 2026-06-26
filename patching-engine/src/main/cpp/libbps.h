#ifndef LIBBPS_H
#define LIBBPS_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifndef SIZE_MAX
#define SIZE_MAX ((size_t)-1)
#endif

struct mem {
    uint8_t * ptr;
    size_t len;
};

enum bpserror {
    bps_ok,//Patch applied or created successfully.
    bps_to_output,//You attempted to apply a patch to its output.
    bps_not_this, //This is not the intended input file for this patch.
    bps_broken,   //This is not a BPS patch, or it's malformed somehow.
    bps_io,       //The patch could not be read.
    bps_identical, //The input files are identical.
    bps_too_big,   //Somehow, you're asking for something a size_t can't represent.
    bps_out_of_mem,//Memory allocation failure.
    bps_canceled,  //The callback returned false.
    bps_shut_up_gcc
};

#ifdef __cplusplus
extern "C" {
#endif

enum bpserror bps_apply(struct mem patch, struct mem in, struct mem * out, struct mem * metadata, bool accept_wrong_input);

#ifdef __cplusplus
}
#endif

#endif // LIBBPS_H
