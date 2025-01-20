import dodex from "dodex";
import input from "dodex-input";
import mess from "dodex-mess";
import "dodex/dist/dodex.min.css";
import "jsoneditor/dist/jsoneditor.min.css"
import jsonEditor from "jsoneditor"
import "@fortawesome/fontawesome-free/js/all.js";
import "@fortawesome/fontawesome-free/js/fontawesome.js";
import "../../css/dodex_handicap.css";
import { groupListener } from "./groups";
window.JSONEditor = jsonEditor;

if (document.querySelector(".top--dodex") === null) {
    // Content for cards A-Z and static card
    dodex.setContentFile("handicap/handicap_info.js");
    const server = window.location.hostname + (window.location.port.length > 0 ? ":" + window.location.port : "");
//    const server = "127.0.0.1:8089"; // (8089)-development (8080)-production
    dodex.init({
      width: 375,
      height: 220,
      left: "50%",
      top: "100px",
      input: input,
      private: "partial",
      replace: true,
      mess: mess,
      server: server
    }).then(function () {

        groupListener(); // Dodex addon for defining user groups.

      // Add in app/personal cards
//      for (let i = 0; i < 4; i++) {
//        dodex.addCard(getAdditionalContent());
//      }
      /* Auto display of widget */
      // dodex.openDodex();
    });
  }
  
  function getAdditionalContent() {
    return {
      cards: {
        card28: {
          tab: "F01", // Only first 3 characters will show on the tab.
          front: {
            content: ``
          },
          back: {
            content: ``
          }
        },
        card29: {
          tab: "F02",
          front: {
            content: ""
          },
          back: {
            content: ""
          }
        },
        card30: {
          tab: "NP",
          front: {
            content: ""
          },
          back: {
            content: ""
          }
        },
        card31: {
          tab: "TW",
          front: {
            content: ''
          },
          back: {
            content: ""
          }
        }
      }
    };
  }
  
