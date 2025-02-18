/**
 * Successful acceptance tests & lints start the production build.
 * Tasks are run serially, 'accept' -> 'pat' -> ('eslint', 'csslint', 'bootlint') -> 'build'
 */
 let chalk;
 import("chalk").then(async C => {
     chalk = new C.Chalk();
     log(await chalk.green("Starting Gulp"));
 });
const { src, /* dest, */ series, parallel, task } = require("gulp");
const runFusebox = require("./fuse.js");
const csslint = require("gulp-csslint");
const eslint = require("gulp-eslint");
const exec = require("child_process").exec;
const log = require("fancy-log");
const path = require("path");
const karma = require("karma");
// const puppeteer = require("puppeteer");
const fs = require("fs");

let lintCount = 0;
let browsers = process.env.USE_BROWSERS;
let useBundler = process.env.USE_BUNDLER !== "false";
let useFtl = true;
let isProduction = false;
let isWatch = false;

process.argv.forEach(function (val, index /* , array */) {
    useFtl = val === "--noftl" && useFtl ? false : useFtl;
    if(index > 2 && process.argv[index].indexOf("-") === -1) {
        process.argv[index] = "";
    }
});
let local = false;
let allure = false;

if (process.argv.includes("-l", 2) || process.argv.includes("-local", 2)) {
    local = true;
}
if (process.argv.includes("-a", 2) || process.argv.includes("-allure", 2)) {
    allure = true;
}

global.local = local;
global.allure = allure;

if (browsers) {
    global.whichBrowser = browsers.split(",");
}

/**
 * Default: Production Acceptance Tests 
 */
const pat = function (done) {
    if (!browsers) {
        global.whichBrowser = [/*"ChromeHeadless",*/ "FirefoxHeadless"];
    }

    karmaServer(done, true, false);
};
/*
 * javascript linter
 */
const esLint = function (cb) {
    var stream = src(["../appl/**/*.js", "../appl/**/*.jsx"])
        .pipe(eslint({
            configFile: ".eslintrc.js",
            quiet: 1,
        }))
        .pipe(eslint.format())
        .pipe(eslint.result(() => {
            // Keeping track of # of javascript files linted.
            lintCount++;
        }))
        .pipe(eslint.failAfterError());

    stream.on("error", function () {
        process.exit(1);
    });

    return stream.on("end", function () {
        log(chalk.blue.bold("# javascript & jsx files linted:", lintCount));
        cb();
    });
};
/*
 * css linter
 */
const cssLint = function (cb) {
    var stream = src(["../appl/css/site.css"
    ])
        .pipe(csslint())
        .pipe(csslint.formatter());

    stream.on("error", function () {
        process.exit(1);
    });

    return stream.on("end", function () {
        cb();
    });
};
/*
 * Bootstrap html linter 
 */
const bootLint = function (cb) {
    return exec("npx gulp --gulpfile Gulpboot.js", function (err, stdout, stderr) {
        log(stdout);
        log(stderr);
        if (err) {
            log("ERROR", err);
        } else {
            log(chalk.green("Bootstrap linting a success"));
        }
        cb();
    });
};
/*
 * Build the application to run karma acceptance tests
 */
const testBuild = function (cb) {
    isWatch = false;
    process.argv[2] = "";
    const props = {
        isKarma: true,
        isHmr: false,
        isWatch: isWatch,
        env: "development",
        useServer: false,
        ftl: false
    };
    let mode = "test";
    const debug = true;
    try {
        runFusebox(mode, fuseboxConfig(mode, props), debug, cb);
    } catch (e) {
        log("Error", e);
    }
};
/*
 * Rebuild the bundle when javascript files change
 */
const watch = function (cb) {
    isWatch = true;
    process.argv[2] = "";
    const props = {
        isKarma: true,
        isHmr: false,
        isWatch: isWatch,
        env: "development",
        useServer: false,
        ftl: false
    };
    let mode = "test";
    const debug = true;
    try {
        runFusebox(mode, fuseboxConfig(mode, props), debug, cb);
    } catch (e) {
        log("Error", e);
    }
};
/*
 * Build the application to the production distribution 
 */
const build = function (cb) {
    isWatch = false;
    process.argv[2] = "";
    if (!useBundler) {
        return cb();
    }
    const props = {
        isKarma: false,
        isHmr: false,
        isWatch: isWatch,
        env: "production",
        useServer: false,
        ftl: false
    };
    let mode = "prod";
    // eslint-disable-next-line no-unused-vars
    isProduction = true;
    const debug = true;
    try {
       return runFusebox(mode, fuseboxConfig(mode, props), debug, cb);
    } catch (e) {
        log("Error", e);
    }
};
/*
 * Build the application to preview the production distribution 
 */
const preview = function (cb) {
    isWatch = false;
    process.argv[2] = "";
    const props = {
        isKarma: false,
        isHmr: false,
        isWatch: isWatch,
        env: "production",
        useServer: true,
        ftl: false
    };
    let mode = "preview";
    // isProduction = true;
    const debug = true;
    try {
        return runFusebox(mode, fuseboxConfig(mode, props), debug, cb);
    } catch (e) {
        log("Error", e);
    }
};
/*
 * Build the application to run karma acceptance tests with hmr
 */
const fuseboxHmr = function (cb) {
    isWatch = true;
    process.argv[2] = "";
    const props = {
        isKarma: false,
        isHmr: true,
        isWatch: isWatch,
        env: "development",
        useServer: true,
        ftl: useFtl
    };
    let mode = "test";
    const debug = true;
    try {
        runFusebox(mode, fuseboxConfig(mode, props), debug, cb);
    } catch (e) {
        log("Error", e);
    }
};

// eslint-disable-next-line no-unused-vars
const setNoftl = function (cb) {
    useFtl = false;
    cb();
};
/*
 * Build the application to run node express so font-awesome is resolved
 */
const fuseboxRebuild = function (cb) {
    isWatch = false;
    process.argv[2] = "";
    const props = {
        isKarma: false,
        isHmr: false,
        isWatch: isWatch,
        env: "development",
        useServer: false,
        ftl: false
    };
    let mode = "test";
    const debug = true;
    try {
        return runFusebox(mode, fuseboxConfig(mode, props), debug, cb);
    } catch (e) {
        log("Error", e);
    }
};
/*
 * copy assets for development
 */
const copy = async function (cb) {
    isWatch = false;
    process.argv[2] = "";
    const props = {
        isKarma: false,
        isHmr: false,
        isWatch: isWatch,
        env: "development",
        useServer: false
    };
    let mode = "copy";
    const debug = true;
    try {
        runFusebox(mode, fuseboxConfig(mode, props), debug);
    } catch (e) {
        log("Error", e);
    }
    cb();
};
/**
 * Run karma/jasmine tests once and exit
 */
const fuseboxAcceptance = function (done) {
    if (!browsers) {
        global.whichBrowser = ["ChromeHeadless", "FirefoxHeadless"];
    }

    karmaServer(done, true, false);
};
/**
 * Continuous testing - test driven development.  
 */
const fuseboxTdd = function (done) {
    if (!browsers) {
        global.whichBrowser = ["Chrome", "Firefox"];
    }

    karmaServer(done, false, true);
};
/**
 * Karma testing under Opera. -- needs configuation  
 */
const tddo = function (done) {
    if (!browsers) {
        global.whichBrowser = ["Opera"];
    }
    karmaServer(done, false, true);
};
/*
*   Make sure gulp terminates.
*/
const final = (done) => {
    done();
    setTimeout(function() {process.exit(0);}, 10);
}

const runProd = series(testBuild, pat, parallel(esLint, cssLint/*, bootLint*/), build, final);
runProd.displayName = "prod";

task(runProd);
exports.default = runProd;
exports.prd = series(parallel(esLint, cssLint/*, bootLint*/), build, final);
exports.preview = preview;
exports.test = series(testBuild, pat, final);
exports.tdd = fuseboxTdd;
exports.hmr = fuseboxHmr;
exports.rebuild = series(fuseboxRebuild, final);
exports.copy = copy;
exports.acceptance = fuseboxAcceptance;
exports.e2e = fuseboxAcceptance;
exports.lint = parallel(esLint, cssLint/*, bootLint*/);
exports.opera = tddo;
// exports.snap = series(karmaServerSnap, final);
exports.watch = watch;

function fuseboxConfig(mode, props) {
    mode = mode || "test";
    // if(process.argv[2]) {
    //     mode = process.argv[2];
    // }
    if (typeof props === "undefined") {
        props = {};
    }
    let toDist = "";
    let isProduction = mode !== "test" && mode !== "copy";
    let distDir = null;
    
    // local means the test applications will not be in the vertx classpath and is only used for local javascript frontend development/testing
    if(local) {
        distDir = mode === "prod" ? path.join(__dirname, "../../spa/react-fusebox") :
            mode === "preview" ? path.join(__dirname, "../../spa/react-fusebox") : path.join(__dirname, "../../spa_test/react-fusebox");
    } 
    // remote(default) puts the application in the vertx classpath and can be accessed in the resulting vertx verticle.
    else {
        distDir = mode === "prod" ? path.join(__dirname, "../../main/resources/META-INF/resources/spa/react-fusebox") :
            mode === "preview" ? path.join(__dirname, "../../main/resources/META-INF/resources/spa/react-fusebox") : path.join(__dirname, "../../main/resources/META-INF/resources/spa_test/react-fusebox");
    }

    let defaultServer = props.useServer;
    let devServe = {
        httpServer: {
            root: local ? "../.." : "../../main/resources/META-INF/resources/",
            port: 3087,
            open: false,
        },
        hmrServer: {
			enabled: props.isHmr && !isProduction,
			useCurrentURL: false,
			connectionURL: "ws://localhost:3087"
		}
    };
    const configure = {
        root: path.join(__dirname, "../.."),
        distRoot: path.join("/", `${distDir}${toDist}`),
        target: "browser",
        env: { NODE_ENV: isProduction ? "production" : "development" },
        entry: path.join(__dirname, "../appl/main.js"),
        cache: {
            root: path.join(__dirname, ".cache"),
            enabled: !isProduction,
            FTL: typeof props.ftl === "undefined" ? true : props.ftl
        },
        sourceMap: !isProduction,
        webIndex: {
            distFileName: distDir + 
                (isProduction && mode === "prod" ? "/appl/testapp.html" : 
                    mode === "preview" ? "/appl/testapp.html" : "/appl/testapp_dev.html"),
            publicPath: "../",
            template: !isProduction ? path.join(__dirname, "../appl/testapp_dev.html") : path.join(__dirname, "../appl/testapp.html")
        },
        watch: props.isWatch && !isProduction,
        hmr: props.isHmr && !isProduction,
        socketURI: "ws://localhost:3087",
        devServer: defaultServer ? devServe : false,
        logging: { level: "succinct" },
        turboMode: true,
        exclude: isProduction ? "**/*test.js" : "",
        resources: {
            resourceFolder: "./appl/resources",
            resourcePublicRoot: isProduction ? "../appl/resources" : "./resources",
        },
        codeSplitting: {
            useHash: isProduction ? true : false
        },
        plugins: []
    };
    return configure;
}

function karmaServer(done, singleRun = false, watch = true) {
    const parseConfig = karma.config.parseConfig;
    const Server = karma.Server;

    parseConfig(
        path.resolve("./karma.conf.js"),
        { port: 9876, singleRun: singleRun, watch: watch },
        { promiseConfig: true, throwErrors: true },
    ).then(
        (karmaConfig) => {
            if (!singleRun) {
                done();
            }
            new Server(karmaConfig, function doneCallback(exitCode) {
                console.log("Karma has exited with " + exitCode);
                if (singleRun) {
                    done();
                }
                if (exitCode > 0) {
                    process.exit(exitCode);
                }
            }).start();
        },
        (rejectReason) => { console.err(rejectReason); }
    );
}

/*
 * Taking a snapshot example
 */
// eslint-disable-next-line no-unused-vars
function karmaServerSnap(done) {
    if (!browsers) {
        global.whichBrowser = ["ChromeHeadless" /* , "FirefoxHeadless" */];
    }

    takeSnapShot(["", "start"], true, done);
    // takeSnapShot(["contact", "contact"], true, done);
    // takeSnapShot(["welcome", "react"], true, done);
    // takeSnapShot(["table/tools", "tools"], true, done);
    // takeSnapShot(["pdf/test", "pdf"], true, done);
            
    // new Server({
    //     configFile: __dirname + "/karma.conf.js",
    //     singleRun: true,
    //     watch: false
    // }, function (result) {
    //     var exitCode = !result ? 0 : result;
    //     done();
    //     if (exitCode > 0) {
    //         takeSnapShot(["", "start"]);
    //         takeSnapShot(["contact", "contact"]);
    //         takeSnapShot(["welcome", "react"]);
    //         takeSnapShot(["table/tools", "tools"]);
    //         takeSnapShot(['pdf/test', 'pdf'])       
    //         process.exit(exitCode);
    //     }
    // }).start();
}

function snap(url, puppeteer, snapshot, close, done) {
    puppeteer.launch().then((browser) => {
        console.log("SnapShot URL", `${url}${snapshot[0]}`);
        let name = snapshot[1];
        browser.newPage().then((page) => {
            page.goto(`${url}${snapshot[0]}`).then(() => {
                page.screenshot({ path: `./snapshots/${name}Acceptance.png` }).then(() => {
                    if(close) {
                        browser.close();
                        done();
                    }
                }).catch((rejected) => {
                    log(rejected);
                });
            }).catch((rejected) => {
                log(rejected);
            });
        }).catch((rejected) => {
            log(rejected);
        });
    }).catch((rejected) => {
        log(rejected);
    });
}

function takeSnapShot(snapshot, close, done) {
    const puppeteer = require("puppeteer");
    let url = "http://localhost:8089/spa_test/react-fusebox/appl/testapp_dev.html#";

    snap(url, puppeteer, snapshot, close, done);
}

// From Stack Overflow - Node (Gulp) process.stdout.write to file
if (process.env.USE_LOGFILE == "true") {
    var util = require("util");
    var logFile = fs.createWriteStream("log.txt", { flags: "w" });
    // Or "w" to truncate the file every time the process starts.
    var logStdout = process.stdout;
    /* eslint no-console: 0 */
    console.log = function () {
        logFile.write(util.format.apply(null, arguments) + "\n");
        logStdout.write(util.format.apply(null, arguments) + "\n");
    };
    console.error = console.log;
}
