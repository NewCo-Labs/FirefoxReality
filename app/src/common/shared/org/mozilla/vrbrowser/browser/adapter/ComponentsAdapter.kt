/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser.adapter

import android.util.Log
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.EngineState
import mozilla.components.browser.state.state.ReaderState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import org.mozilla.geckoview.GeckoSession
import org.mozilla.vrbrowser.browser.components.GeckoEngineSession
import org.mozilla.vrbrowser.browser.engine.Session
import org.mozilla.vrbrowser.utils.SystemUtils

class ComponentsAdapter private constructor(
        val store: BrowserStore = BrowserStore()
) {
    private val LOGTAG = SystemUtils.createLogtag(ComponentsAdapter::class.java)

    companion object {
        private val instance: ComponentsAdapter = ComponentsAdapter()

        @JvmStatic
        fun get(): ComponentsAdapter = instance
    }

    fun addSession(session: Session) {
        val tab = session.toTabSessionState()
        if (store.state.tabs.stream().noneMatch {
                    it.id == tab.id
        }) {
            store.dispatch(TabListAction.AddTabAction(
                    tab = tab
            ))

        } else {
            Log.e(LOGTAG, "Duplicated session add attempt: " + tab.id)
        }
    }

    fun removeSession(id: String) {
        store.dispatch(TabListAction.RemoveTabAction(
                tabId = id
        ))
    }

    fun selectSession(session: Session) {
        store.dispatch(TabListAction.SelectTabAction(
                tabId = session.id
        ))
    }

    fun link(tabId: String, geckoSession: GeckoSession) {
        store.dispatch(EngineAction.LinkEngineSessionAction(
                tabId,
                GeckoEngineSession(geckoSession)
        ))
    }

    fun unlink(tabId: String) {
        store.dispatch(EngineAction.UnlinkEngineSessionAction(
                tabId
        ))
    }
}

private fun Session.toTabSessionState(): TabSessionState {
    return TabSessionState(
            id = id,
            content = ContentState(
                    url = currentUri,
                    private = isPrivateMode,
                    title = currentTitle
            ),
            parentId = null,
            extensionState = emptyMap(),
            readerState = ReaderState(),
            engineState = EngineState(
                    GeckoEngineSession(geckoSession),
                    null
            )
    )
}