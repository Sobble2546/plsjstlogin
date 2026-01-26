# Please Just Login

Froked from [OpeNLogin](https://github.com/nickuc/OpeNLogin).  
In goal of adding more features 

## Use of genrative ai ⚠️
Generative AI has been used in the development of **this fork** to assist with implementing and refining new features.

### Features:
* Login from Spawn and telport to last location (WITH LESS FAILURE RATES!)
![Preview](https://i.postimg.cc/sXmWc3yk/lastloc.gif)
<small> So dont worry about your players randomly teleporting into spawn after login </small>

* Hide inventory when on login with [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)
* **Admin commands** 
```
/plsjustadmin rmpass <Username> #Removes Passwword for that user (acts like Unregister)
/plsjustadmin migrate <OldUsername> <NewUsername> # Migrates from old username to new username (Untested Use it at your on risk)
/plsjustadmin changepass <Username> <New Password> #Changes Password
```



### For development:

#### Gradle:
```
repositories {
    maven { 
        url = uri('https://repo.nickuc.com/maven-releases/') 
    }
}

dependencies {
    compileOnly('com.nickuc.openlogin:openlogin-universal:1.3')
}
```

#### Maven:
```xml
<repositories>
  <repository>
    <id>nickuc-repo</id>
    <url>https://repo.nickuc.com/maven-releases/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.nickuc.openlogin</groupId>
    <artifactId>openlogin-universal</artifactId>
    <version>1.3</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

