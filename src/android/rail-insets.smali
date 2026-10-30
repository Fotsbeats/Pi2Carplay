.method public static boxKeyboardRailInsets(Landroid/view/InsetsState;I)Landroid/view/InsetsState;
    .registers 8
    const/16 v0, 0x7db
    if-eq p1, v0, :rail_ready
    const/16 v0, 0x7dc
    if-ne p1, v0, :rail_done
    :rail_ready
    const-string v0, "sys.boxhome.rail.ready"
    const/4 v1, 0x0
    invoke-static {v0, v1}, Landroid/os/SystemProperties;->getBoolean(Ljava/lang/String;Z)Z
    move-result v0
    if-eqz v0, :rail_done
    invoke-virtual {p0}, Landroid/view/InsetsState;->getDisplayFrame()Landroid/graphics/Rect;
    move-result-object v0
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I
    move-result v1
    const/16 v2, 0x140
    if-lt v1, v2, :rail_done
    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I
    move-result v1
    if-lez v1, :rail_done
    new-instance v1, Landroid/view/InsetsState;
    const/4 v2, 0x1
    invoke-direct {v1, p0, v2}, Landroid/view/InsetsState;-><init>(Landroid/view/InsetsState;Z)V
    # InsetsSource.createId(null, 31, navigationBars()), separate from stock sources.
    const/16 v2, 0x3e1
    const/4 v3, 0x2
    invoke-virtual {v1, v2, v3}, Landroid/view/InsetsState;->getOrCreateSource(II)Landroid/view/InsetsSource;
    move-result-object v2
    const/4 v3, 0x0
    const/16 v4, 0x68
    iget v5, v0, Landroid/graphics/Rect;->bottom:I
    invoke-virtual {v2, v3, v3, v4, v5}, Landroid/view/InsetsSource;->setFrame(IIII)Landroid/view/InsetsSource;
    const/4 v3, 0x1
    invoke-virtual {v2, v3}, Landroid/view/InsetsSource;->setVisible(Z)Landroid/view/InsetsSource;
    return-object v1
    :rail_done
    return-object p0
.end method
