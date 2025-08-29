package com.geeksville.mesh.prefs;

public class UserPrefs {

    public static class Hunting {
        public static final String SHARED_HUNT_PREFS = "hunt_prefs"; //nome shared pref

        // ----> params
        public static final String HUNT_MODE = "hunting_mode";
        public static final String BACKGROUND_HUNT = "background_hunt";
        public static final String HUNT_DOMAIN = "hunt_domain";
        public static final String HUNT_TOKEN = "hunt_token";
        public static final String BACKGROUND_HUNT_MODE = "background_hunt_mode";
        public static final String BACKGROUND_MODE_FAST = "FAST";
        public static final String BACKGROUND_MODE_MEDIUM = "MEDIUM";
        public static final String BACKGROUND_MODE_SLOW = "SLOW";
        public static final String BACKGROUND_MODE_SUPER_SLOW = "SUPER_SLOW";
        // <---- end params
    }

    public static class PlannedMessage {

        // ----> nome shared prefs che immagazzina lo stato del servizio di planning
        public static final String SHARED_PLANMSG_PREFS_STATUS = "planmsg_status";
        // ----> params
        public static final String PLANMSG_SERVICE_ACTIVE = "status"; //param
        // <---- end params


        // ----> nome shared prefs che immagazzina tutte le pianificaizoni
        public static final String SHARED_PLANNED_MSG_PREFS = "planmsg_prefs";
        //<---- no params
    }


}
