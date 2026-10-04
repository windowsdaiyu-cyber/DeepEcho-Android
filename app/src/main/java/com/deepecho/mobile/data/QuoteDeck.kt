package com.deepecho.mobile.data

import java.util.Calendar

/**
 * Original short DeepEcho micro-quotes. No remote service is needed.
 * The selection rotates with date + hour so Home feels fresh throughout the day.
 */
object QuoteDeck {
    private val quotes = listOf(
        "Aaj ka mood, aaj ka soundtrack.",
        "Music on. Noise off.",
        "Ek achha gaana pura scene badal deta hai.",
        "Jo vibe chahiye, woh play karo.",
        "Har repeat ke peeche ek reason hota hai.",
        "Aaj kuch naya suno.",
        "Your next favorite song might be one tap away.",
        "Thoda volume, thoda peace.",
        "Playlist chhoti ho sakti hai, mood nahi.",
        "Gaane yaad rakhte hain jo hum bhool jaate hain.",
        "Morning ko soft start do.",
        "Aaj ka first track matter karta hai.",
        "Good music makes ordinary moments cinematic.",
        "Ek familiar voice bhi comfort hoti hai.",
        "Skip less. Feel more.",
        "Apne mood ko shuffle mat karo, music ko karo.",
        "Sometimes the right song is enough.",
        "Aaj ka background music choose kar lo.",
        "Headphones lagao, duniya thodi quiet ho jaati hai.",
        "Achi melody ka koi shortcut nahi hota.",
        "Late-night songs hit different.",
        "Work mode ko rhythm chahiye.",
        "Travel ka half fun soundtrack hota hai.",
        "Repeat button bhi ek emotion hai.",
        "Kuch songs sirf sunai nahi dete, feel hote hain.",
        "New day, new queue.",
        "Aaj nostalgia chalega ya discovery?",
        "Let the next track surprise you.",
        "Music is the cleanest kind of reset.",
        "Ek beat se energy change ho sakti hai.",
        "Soft songs, sharp focus.",
        "Mood low ho toh volume smart rakho.",
        "Favorite artist kabhi random nahi hota.",
        "Aaj ka vibe tumhari history se better samjhenge.",
        "Keep the queue personal.",
        "Discovery tab best hoti hai jab familiar bhi lage.",
        "Har artist ek naya world kholta hai.",
        "One song can rescue a boring minute.",
        "Music ko background mat samjho, kabhi kabhi wahi main scene hai.",
        "Aaj apne taste ko thoda expand karo.",
        "Old favorite, fresh mood.",
        "Perfect track milte hi sab smooth lagta hai.",
        "Your taste is a fingerprint.",
        "Queue ko smart rakho, mood ko simple.",
        "Good afternoon deserves a better soundtrack.",
        "Evening ko thoda glow aur thoda music.",
        "Night mode + favorite song = enough.",
        "Early hours deserve gentle tracks.",
        "Aaj kis artist ka phase hai?",
        "One more song rarely means one more song.",
        "The best recommendations feel obvious after you hear them.",
        "Music memory se tez travel karta hai.",
        "Acha chorus din bhar saath reh sakta hai.",
        "Let the bass carry the boring parts.",
        "Kabhi kabhi shuffle hi best decision hota hai.",
        "Your library is your little universe.",
        "Aaj kisi underrated track ko chance do.",
        "Familiar lyrics, different day.",
        "A song can be old and still feel new.",
        "Keep listening until the mood clicks.",
        "Deep focus needs the right texture.",
        "Weekend ho ya weekday, vibe custom honi chahiye.",
        "The next artist might become your next obsession.",
        "Tiny moments deserve big sound.",
        "Music makes waiting less boring.",
        "Aaj ka theme bhi mood ke saath change karo.",
        "Lyrics kabhi kabhi music se zyada seedha hit karte hain.",
        "A good queue should know when not to repeat itself.",
        "Explore smart, not random.",
        "Your recent plays are clues, not limits.",
        "Aaj ka recommendation kal ka favorite ho sakta hai.",
        "Silence bhi achhi hai, par abhi playlist better hai.",
        "Keep the sound clean and the queue curious.",
        "Songs end. Vibes carry forward.",
        "One tap, one mood shift.",
        "Aaj apne top artist se start karte hain.",
        "Every replay teaches the app your taste.",
        "A little discovery keeps music fresh.",
        "Find the song you did not know you needed.",
        "Personal music should actually feel personal.",
        "The right next song should feel effortless.",
        "Let DeepEcho handle the next track.",
        "Your soundtrack should move with your day.",
        "Morning calm, afternoon energy, night glow.",
        "Taste changes. Home should change with it.",
        "Less searching, more listening.",
        "Your favorites are signals, not a cage.",
        "Aaj ka quote badlega, taste bhi evolve karega.",
        "Keep one ear on favorites and one on discovery.",
        "Good music does not need a reason.",
        "A smooth player should disappear behind the music.",
        "Fast play, clean sound, smart next.",
        "Aaj jo sunoge, kal Home thoda aur tumhara hoga."
    )

    fun current(): String {
        val c = Calendar.getInstance()
        val day = c.get(Calendar.DAY_OF_YEAR)
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minuteBucket = c.get(Calendar.MINUTE) / 20
        val index = (day * 31 + hour * 7 + minuteBucket) % quotes.size
        return quotes[index]
    }
}
