# Please Just Login

Froked from [OpeNLogin](https://github.com/nickuc/OpeNLogin).  
In goal of adding more features 

## Use of genrative ai ⚠️
Generative AI has been used in the development of **this fork** to assist with implementing and refining new features.

### Features:
* Login from Spawn and telport to last location (WITH LESS FAILURE RATES!)
* Hide inventory when on login with [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)
* **Admin commands** to
    * Unrigester or Remove the password of a user
    * Migrate from old username to a new username (beta)
    * Change player passwords securely


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

