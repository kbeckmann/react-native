/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

#import "RCTNavigatorManager.h"

#import "RCTBridge.h"
#import "RCTConvert.h"
#import "RCTNavigator.h"
#import "RCTUIManager.h"
#import "UIView+React.h"

@implementation RCTNavigatorManager

RCT_EXPORT_MODULE()

- (UIView *)view
{
  return [[RCTNavigator alloc] initWithBridge:self.bridge];
}

RCT_EXPORT_VIEW_PROPERTY(requestedTopOfStack, NSInteger)
RCT_EXPORT_VIEW_PROPERTY(interactivePopGestureEnabled, BOOL)
RCT_EXPORT_VIEW_PROPERTY(onNavigationProgress, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onNavigationComplete, RCTBubblingEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onSearchText, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onSearchPressed, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onSearchCancelled, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(backGestureEnabled, BOOL)

RCT_EXPORT_METHOD(showSearch:(nonnull NSNumber *)reactTag
                      prompt:(NSString *)prompt
                 placeholder:(NSString *)placeholder
                        text:(NSString *)text
                     focused:(BOOL)focused)
{
  [self.bridge.uiManager addUIBlock:
   ^(__unused RCTUIManager *uiManager, NSDictionary<NSNumber *, RCTNavigator *> *viewRegistry){
     RCTNavigator *navigator = viewRegistry[reactTag];
     if ([navigator isKindOfClass:[RCTNavigator class]]) {
       [navigator showSearch:prompt
                 placeholder:placeholder
                        text:text
                     focused:focused
        ];
     }
   }];
}

RCT_EXPORT_METHOD(hideSearch:(nonnull NSNumber *)reactTag)
{
  [self.bridge.uiManager addUIBlock:
   ^(__unused RCTUIManager *uiManager, NSDictionary<NSNumber *, RCTNavigator *> *viewRegistry){
     RCTNavigator *navigator = viewRegistry[reactTag];
     if ([navigator isKindOfClass:[RCTNavigator class]]) {
       [navigator hideSearch];
     }
   }];
}

// TODO: remove error callbacks
RCT_EXPORT_METHOD(requestSchedulingJavaScriptNavigation:(nonnull NSNumber *)reactTag
                  errorCallback:(__unused RCTResponseSenderBlock)errorCallback
                  callback:(RCTResponseSenderBlock)callback)
{
  [self.bridge.uiManager addUIBlock:
   ^(__unused RCTUIManager *uiManager, NSDictionary<NSNumber *, RCTNavigator *> *viewRegistry){
    RCTNavigator *navigator = viewRegistry[reactTag];
    if ([navigator isKindOfClass:[RCTNavigator class]]) {
      BOOL wasAcquired = [navigator requestSchedulingJavaScriptNavigation];
      callback(@[@(wasAcquired)]);
    } else {
      RCTLogError(@"Cannot set lock: %@ (tag #%@) is not an RCTNavigator", navigator, reactTag);
    }
  }];
}

@end
