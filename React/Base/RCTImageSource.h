/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

#import <Foundation/Foundation.h>

#import "RCTConvert.h"

/**
 * Object containing an image URL and associated metadata.
 */
@interface RCTImageSource : NSObject

@property (nonatomic, strong, readonly) NSURL *imageURL;
@property (nonatomic, assign, readonly) CGSize size;
@property (nonatomic, assign, readonly) CGFloat scale;
@property (nonatomic, strong, readonly) NSDictionary *headers;

/**
 * Create a new image source object.
 * Pass a size of CGSizeZero if you do not know or wish to specify the image
 * size. Pass a scale of zero if you do not know or wish to specify the scale.
 */
- (instancetype)initWithURL:(NSURL *)url
                       size:(CGSize)size
                      scale:(CGFloat)scale
                    headers:(NSDictionary *)headers;

/**
 * Create a copy of the image source with the specified size and scale.
 */
- (instancetype)imageSourceWithSize:(CGSize)size scale:(CGFloat)scale;

@end

@interface RCTConvert (ImageSource)

+ (RCTImageSource *)RCTImageSource:(id)json;

@end
