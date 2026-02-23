<p align="center">
  <img src="https://darkmesh.neocities.org/images/dmlogo4.png" width="120" alt="DarkMesh Logo">
</p>
<h1 align="center">DarkMesh-Android</h1>
<p align="center">
  An independent development from Meshtastic LLC. focused on Privacy and Field Operators.
  Licensed under <b>GPL-3.0</b>.
</p>

<p align="center">
<a href="https://darkmesh.neocities.org">OFFICIAL WEBSITE</a>
</p> 

> [!WARNING]
> **DarkMesh is designed for FULL-OFFGRID usage only.**  
> If you plan to use MQTT or control your node remotely via Wi-Fi, this project is **not suitable** for your use case.

> [!IMPORTANT]
>The Android application and the Darkmesh firmware are provided without any guarantee of operation or reliability, neither express nor implied. Use takes place under the user’s sole responsibility. The author of the software declines all responsibility for any violations of regulations, as well as for direct, indirect, incidental, or consequential damages arising from the use, misuse, or inability to use the software.
> Use of the software entails the implicit acceptance of the conditions indicated above.

---

## 🛰️ Overview

**DarkMesh** is an independent fork and autonomous development of the **Meshtastic** project,  
focused on **scheduled messaging** and **metadata forwarding** to **online mapping platforms** (optional and disabled by default).

Designed for **advanced users** and **field operators**, DarkMesh introduces a range of  
new features to enhance decentralized networking and node-to-node interaction  
in tactical or remote scenarios.


---

## ⚙️ Core Features

### 🔭 Hunting Mode
Streams real-time telemetry from mesh nodes to a **remote server**  
for display on a **dynamic online map**.  
Ideal for tracking, live monitoring, or operational coordination.  
[_A short setup guide is available here!_](https://mesh.loracity.it/mappa-dei-nodi-meshtastic-ricevuti-via-radio/)

### 🕒 Message Scheduling
Plan and schedule **private** or **group** messages to be automatically  
sent at specific times.  
Useful for automations, time-based alerts, or mission coordination.

### 📡 Distress Beacon
Send **periodic beacons** with customizable text payloads.  
Beacons can be used for **automatic identification**, **emergency status reporting**,  
or **node discovery** within the network.

### 📡 Traceroute Map visualization
It is possibile to visualize traceroutes on the application map. Forward route and Backward Route.
It is also shown the total distance of the roundtrip.

### 🌍 Efficient Plus Codes
You can configure your distress beacon by using short Plus Codes inside text messages, typically encoded in ~8–9 ASCII characters.
This ensures a significantly smaller payload over the mesh, resulting in less airtime and higher reliability.

### 💾 Database Import/Export
The database import/export feature has been introduced using a proprietary .dmdb file format.
You can export the entire database or selectively export only nodes marked as favorites. This can be useful if you want to share your nodes with someone.


---

## 🔍 Extended Verbosity and Transparency

DarkMesh offers **enhanced network transparency** with real-time user feedback.

The app displays **toast notifications** whenever:
- a message is **retransmitted** across the mesh, or  
- a **trace request** targets your node.

This behavior provides full awareness of ongoing network events and  
makes debugging or situational analysis far more intuitive.

---

## 🧩 License

This project is released under the terms of the **GNU General Public License v3.0 (GPL-3.0)**.  
See the [LICENSE](LICENSE) file for details.

---

## 🧠 Acknowledgments

DarkMesh is based on the open-source **Meshtastic** ecosystem.  
All credit for the base protocol, APIs, and firmware design goes to  
the [Meshtastic](https://github.com/meshtastic) community and its contributors.

---

<section class="intro">
  
  <p>
    DarkMesh has grown over time and is now adopted by a wide range of users.
    Its evolution requires continuous maintenance, regular updates, and the development of new features (it is developed and maintained solely by me).
    Voluntary support helps sustain ongoing development and ensures the long-term continuity of the project.
  </p>

  <p align="center">
    <a href="https://ko-fi.com/darkmesh" target="_blank" rel="noopener noreferrer">
      <img src="https://frompage2screen.com/wp-content/uploads/2024/01/buymeakofi.webp"
           alt="Support DarkMesh via PayPal"
           width="180">
    </a>
  </p>

</section>


<p align="center">
  <i>DarkMesh — decentralized communication, refined for the field.</i>
</p>
