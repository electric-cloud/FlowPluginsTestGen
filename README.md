1. use command to build artifact: 
./gradlew distZip
2. then unzip file in folder build/distributions/
   unzip FlowPluginsTestGen-1.0-SNAPSHOT.zip
3. cli utility will be located in path: 
./FlowPluginsTestGen/build/distributions/FlowPluginsTestGen-1.0-SNAPSHOT/bin/FlowPluginsTestGen

4. Go to main plugin folder, example: EC-Kubectl
5. Create a file "testConfig/testspec.yaml" which contains tests specification (example locates in folder "resources"): 
EC-Kubectl
└── testConfig
    └── testspec.yaml
6. launch cli tool (./FlowPluginsTestGen/build/distributions/FlowPluginsTestGen-1.0-SNAPSHOT/bin/FlowPluginsTestGen) from plugin directory