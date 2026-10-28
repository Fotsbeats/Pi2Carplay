# Adaptive display candidate

The public adapter reads the selected canvas and frame rate from `carplay_reverse_get_video_parameters`, then the selected `gActiveViewArea`, `gHaveSafeAreas` and `gSafeAreas` from the pinned protocol SDK. SDK rows are `height,width,x,y`, with 32 possible areas. Their ABI was traced in the supplied binary, SHA256 `006947de8e64acc43be7432aafd0aa36647c3052fbfb487584854799b91790ac`.

When the selected area has safe-area metadata, its rectangle is used. Without safe-area metadata, the complete negotiated canvas is used. No vehicle-name detection or fixed vehicle offset exists in the adapter. Tests cover multiple advertised areas and selection of the active rectangle. Fixture dimensions are not runtime defaults.

After validation the adapter atomically publishes seven integers: canvas width, canvas height, frame rate, content x, content y, content width, content height. The wrapper waits for this per-session file before changing Android's logical size and starting capture. The mirror, packed GPU I420 conversion, encoder and touch transformation use that same geometry. Android's persistent navigation rail occupies 104 pixels inside the content area; it is unrelated to head-unit reserved space.

Bounds: even canvas dimensions from 320x240 through 2560x1440, content at least 320x240 wholly inside the canvas, and frame rate capped at 60. These are allocation/format bounds, not measured performance guarantees. Invalid geometry aborts rather than retaining a previous vehicle's calibration. A mid-session geometry change ends streaming safely; reconnect/restart is required. Each bridge invocation clears the previous geometry file. Runtime resizing without reconnect and rotated/portrait layouts are not qualified.

Host tests cover active-area selection, no reserved area, offsets on both axes, invalid rectangles, packed rows, real x264 SPS dimensions, timestamps and requested IDRs. `GpuConversionSelfCheck` exercises five layouts against native libyuv on the Pi, including odd insets and a padded readback row.

On-device GPU conversion and live capture checks pass. Adaptive display negotiation has passed vehicle testing. More vehicles and display sizes need testing; successful negotiation alone does not establish picture fit, touch alignment or sustained frame rate.
