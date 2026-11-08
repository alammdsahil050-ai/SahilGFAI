package com.sahil.gfai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

/** Phone-control functions that Gemini can call. */
object Tools {
    private fun s(d: String) = JSONObject().put("type", "STRING").put("description", d)
    private fun n(d: String) = JSONObject().put("type", "NUMBER").put("description", d)
    private fun fn(name: String, desc: String, props: JSONObject? = null, req: List<String> = emptyList()): JSONObject {
        val o = JSONObject().put("name", name).put("description", desc)
        if (props != null) o.put("parameters", JSONObject().put("type", "OBJECT").put("properties", props).put("required", JSONArray(req)))
        return o
    }

    fun declarations(): JSONArray = JSONArray().put(JSONObject().put("functionDeclarations", JSONArray()
        .put(fn("open_app", "Open an installed app by its name.", JSONObject().put("app_name", s("App name, e.g. WhatsApp, YouTube")), listOf("app_name")))
        .put(fn("read_screen", "Read all visible text on the current phone screen."))
        .put(fn("tap_text", "Tap a button/item on screen that has this text.", JSONObject().put("text", s("Visible text")), listOf("text")))
        .put(fn("tap_xy", "Tap screen coordinates in pixels.", JSONObject().put("x", n("x")).put("y", n("y")), listOf("x", "y")))
        .put(fn("type_text", "Type text into the focused input field.", JSONObject().put("text", s("Text to type")), listOf("text")))
        .put(fn("scroll", "Scroll the screen.", JSONObject().put("direction", s("up, down, left or right")), listOf("direction")))
        .put(fn("press_back", "Press the Back button."))
        .put(fn("press_home", "Go to the home screen."))
        .put(fn("open_recents", "Open recent apps."))
        .put(fn("call_contact", "Call a saved contact by name.", JSONObject().put("name", s("Contact name")), listOf("name")))))

    fun run(ctx: Context, name: String, a: JSONObject): String {
        val svc = AssistantAccessibilityService.instance
        fun needSvc(): String? = if (svc == null) "Accessibility service on nahi hai. User ko Permissions me enable karne bolo." else null
        return try {
            when (name) {
                "open_app" -> openApp(ctx, a.getString("app_name"))
                "read_screen" -> needSvc() ?: svc!!.readScreen()
                "tap_text" -> needSvc() ?: if (svc!!.clickText(a.getString("text"))) "tapped" else "text not found"
                "tap_xy" -> needSvc() ?: if (svc!!.tap(a.getDouble("x").toFloat(), a.getDouble("y").toFloat())) "tapped" else "failed"
                "type_text" -> needSvc() ?: if (svc!!.typeText(a.getString("text"))) "typed" else "no focused input field"
                "scroll" -> needSvc() ?: if (svc!!.scroll(a.optString("direction", "down"))) "scrolled" else "failed"
                "press_back" -> needSvc() ?: global(svc!!, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                "press_home" -> needSvc() ?: global(svc!!, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                "open_recents" -> needSvc() ?: global(svc!!, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                "call_contact" -> callContact(ctx, a.getString("name"))
                else -> "unknown tool"
            }
        } catch (e: Exception) { "error: ${e.message}" }
    }

    private fun global(svc: AssistantAccessibilityService, action: Int): String { svc.performGlobalAction(action); return "ok" }

    private fun launchCtx(ctx: Context): Context = AssistantAccessibilityService.instance ?: ctx

    private fun openApp(ctx: Context, query: String): String {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(i, 0).firstOrNull {
            it.loadLabel(pm).toString().contains(query, ignoreCase = true)
        } ?: return "App '$query' nahi mila."
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return "Launch nahi ho paya."
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchCtx(ctx).startActivity(launch)
        return "opened ${match.loadLabel(pm)}"
    }

    private fun callContact(ctx: Context, name: String): String {
        val c = ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)
        val num = c?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return "Contact '$name' nahi mila."
        launchCtx(ctx).startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "calling $name"
    }
}
